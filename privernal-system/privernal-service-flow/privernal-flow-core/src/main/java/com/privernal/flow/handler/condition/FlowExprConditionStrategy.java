/*
 * Copyright 2025-2026 saperliu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.privernal.flow.handler.condition;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.privernal.common.core.utils.StringUtils;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import com.privernal.dynamic.dbs.groovy.GroovySandboxConfig;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.strategy.ConditionStrategy;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 网关流转条件表达式策略（设计器「节点流转条件」弹窗产物）。
 * <p>
 * 设计器把条件存为 {@code wfexpr@@{"expr":"报销金额>10000","labelMap":{"报销金额":"baoxiao_je"}}}：
 * 表达式面向业务人员用字段显示名（label）编写，labelMap 负责翻译回数据字段名（field）。
 * 本策略注册进 warm-flow 的条件策略链（ExpressionUtil），互斥/包含网关路由时由引擎按
 * {@code wfexpr} 前缀分发到这里，用 Groovy 求值出布尔结果决定边的走向。
 * <p>
 * 变量来源：发起/审批时写入实例变量的表单数据（含驼峰别名）+ 网关上下文保留变量
 * （{@link #INITIATOR} 等，由 FlowTaskServiceImpl / CommonController 在组装流程变量时注入）。
 * 函数与设计器弹窗函数面板（phoenix EXPRESSION_FUNCTIONS）同名：SUM/MAX/LEN/CONTAINS/IF/HAS_ROLE 等。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class FlowExprConditionStrategy implements ConditionStrategy {

    /** 条件类型标识，设计器与存储格式（wfexpr@@payload）依赖该值，不可变更 */
    public static final String TYPE = "wfexpr";

    /** 网关上下文保留变量：发起人（用户id） */
    public static final String INITIATOR = "__initiator";
    /** 网关上下文保留变量：发起人姓名 */
    public static final String INITIATOR_NAME = "__initiator_name";
    /** 网关上下文保留变量：发起人主部门id */
    public static final String INITIATOR_DEPT = "__initiator_dept";
    /** 网关上下文保留变量：发起人所属组织id */
    public static final String INITIATOR_ORG = "__initiator_org";
    /** 网关上下文保留变量：发起人角色名集合（Collection&lt;String&gt;，HAS_ROLE 用） */
    public static final String INITIATOR_ROLES = "__initiator_roles";

    /** 表达式长度上限（flow_skip.condition_value 为 varchar(1000)，同时防超长编译拖垮线程池） */
    private static final int MAX_EXPRESSION_LENGTH = 900;
    private static final int MAX_CACHED_SCRIPTS = 200;
    private static final long EVAL_TIMEOUT_SECONDS = 3;

    private GroovyShell shell;
    private final Map<String, Class<?>> scriptCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Class<?>> eldest) {
                    return size() > MAX_CACHED_SCRIPTS;
                }
            });

    private ExecutorService evalExecutor;

    @PostConstruct
    public void register() {
        // S9-4：网关条件表达式是流程设计者可编辑的 Groovy，必须套与表单后台脚本
        // 同一的白名单沙箱（GroovySandboxConfig），否则流程设计入口即代码执行入口
        CompilerConfiguration configuration = GroovySandboxConfig.sandboxed();
        configuration.setSourceEncoding("UTF-8");
        this.shell = new GroovyShell(getClass().getClassLoader(), configuration);
        this.evalExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "flow-gateway-expr");
            thread.setDaemon(true);
            return thread;
        });
        // 注册进引擎条件策略链：互斥(serial)/包含(inclusive)网关路由时按 wfexpr 前缀分发
        ExpressionUtil.setExpression(this);
        log.info("网关流转条件表达式策略 {} 已注册", TYPE);
    }

    @PreDestroy
    public void shutdown() {
        if (evalExecutor != null) {
            evalExecutor.shutdownNow();
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 引擎在网关路由时调用。expression 为剥掉 {@code wfexpr@@} 前缀后的载荷：
     * JSON {@code {"expr":..,"labelMap":{..}}}，或历史裸表达式（容错按原样求值）。
     */
    @Override
    public Boolean eval(String expression, Map<String, Object> variables) {
        String trimmed = expression == null ? "" : expression.trim();
        if (trimmed.isEmpty()) {
            throw new FlowException("流转条件表达式为空");
        }
        if (trimmed.length() > MAX_EXPRESSION_LENGTH) {
            throw new FlowException("流转条件表达式超长（上限 " + MAX_EXPRESSION_LENGTH + " 字符）");
        }
        String expr = trimmed;
        JSONObject labelMap = null;
        if (trimmed.startsWith("{")) {
            try {
                JSONObject config = JSONUtil.parseObj(trimmed);
                expr = config.getStr("expr");
                labelMap = config.getJSONObject("labelMap");
            } catch (Exception e) {
                // 非 JSON 按裸表达式处理（对历史数据 / 手工配置容错）
                log.warn("流转条件载荷非 JSON，按裸表达式求值：{}", trimmed);
            }
        }
        if (StringUtils.isEmpty(expr)) {
            throw new FlowException("流转条件表达式为空");
        }
        String script = translateLabels(expr, labelMap);
        Binding binding = new Binding();
        if (variables != null) {
            variables.forEach(binding::setVariable);
        }
        bindFunctions(binding);
        Object result = run(script, binding);
        if (!(result instanceof Boolean decision)) {
            throw new FlowException("流转条件表达式结果必须为布尔值：" + expr);
        }
        log.debug("网关条件求值 expr={} => {}", expr, decision);
        return decision;
    }

    /**
     * 字段显示名 → 数据字段名：按名称长度降序做原子匹配（避免短名吞掉长名前缀），
     * 单引号字符串字面量内的文本不替换（'设备A' 这类取值里常含字段名子串）。
     */
    private String translateLabels(String expr, JSONObject labelMap) {
        if (labelMap == null || labelMap.isEmpty()) {
            return expr;
        }
        List<String> labels = new ArrayList<>(labelMap.keySet());
        labels.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder out = new StringBuilder(expr.length());
        boolean inString = false;
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '\'') {
                inString = !inString;
                out.append(c);
                i++;
                continue;
            }
            if (inString) {
                out.append(c);
                i++;
                continue;
            }
            boolean matched = false;
            for (String label : labels) {
                if (!label.isEmpty() && expr.startsWith(label, i)) {
                    String field = labelMap.getStr(label);
                    if (StringUtils.isNotEmpty(field)) {
                        out.append(field);
                        i += label.length();
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 绑定弹窗函数面板同名函数（phoenix EXPRESSION_FUNCTIONS）与 HAS_ROLE */
    private void bindFunctions(Binding binding) {
        binding.setProperty("ABS", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return Math.abs(toNumber(value, "ABS"));
            }
        });
        binding.setProperty("CEILING", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return Math.ceil(toNumber(value, "CEILING"));
            }
        });
        binding.setProperty("FLOOR", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return Math.floor(toNumber(value, "FLOOR"));
            }
        });
        binding.setProperty("ROUND", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value, Object scale) {
                int digits = scale == null ? 0 : (int) toNumber(scale, "ROUND");
                return BigDecimal.valueOf(toNumber(value, "ROUND")).setScale(digits, RoundingMode.HALF_UP).doubleValue();
            }
        });
        binding.setProperty("SUM", sumClosure());
        binding.setProperty("MAX", extremumClosure(true));
        binding.setProperty("MIN", extremumClosure(false));
        binding.setProperty("LEN", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return value == null ? 0 : String.valueOf(value).length();
            }
        });
        binding.setProperty("CONCAT", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object... args) {
                StringBuilder builder = new StringBuilder();
                for (Object arg : args) {
                    builder.append(arg == null ? "" : String.valueOf(arg));
                }
                return builder.toString();
            }
        });
        binding.setProperty("LEFT", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value, Object count) {
                String text = value == null ? "" : String.valueOf(value);
                int n = (int) toNumber(count, "LEFT");
                return text.substring(0, Math.min(Math.max(n, 0), text.length()));
            }
        });
        binding.setProperty("RIGHT", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value, Object count) {
                String text = value == null ? "" : String.valueOf(value);
                int n = (int) toNumber(count, "RIGHT");
                return text.substring(Math.max(text.length() - Math.max(n, 0), 0));
            }
        });
        binding.setProperty("TRIM", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return value == null ? "" : String.valueOf(value).trim();
            }
        });
        binding.setProperty("UPPER", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return value == null ? "" : String.valueOf(value).toUpperCase();
            }
        });
        binding.setProperty("LOWER", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return value == null ? "" : String.valueOf(value).toLowerCase();
            }
        });
        binding.setProperty("REPLACE", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value, Object oldText, Object newText) {
                String text = value == null ? "" : String.valueOf(value);
                return text.replace(oldText == null ? "" : String.valueOf(oldText),
                        newText == null ? "" : String.valueOf(newText));
            }
        });
        binding.setProperty("CONTAINS", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value, Object part) {
                return value != null && part != null && String.valueOf(value).contains(String.valueOf(part));
            }
        });
        binding.setProperty("TODAY", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall() {
                return DateUtil.today();
            }
        });
        binding.setProperty("NOW", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall() {
                return DateUtil.now();
            }
        });
        binding.setProperty("YEAR", datePartClosure("year"));
        binding.setProperty("MONTH", datePartClosure("month"));
        binding.setProperty("DAY", datePartClosure("day"));
        binding.setProperty("DAYS", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object endDate, Object startDate) {
                Date end = toDate(endDate, "DAYS");
                Date start = toDate(startDate, "DAYS");
                return DateUtil.betweenDay(start, end, true);
            }
        });
        binding.setProperty("IF", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object condition, Object whenTrue, Object whenFalse) {
                return Boolean.TRUE.equals(condition) ? whenTrue : whenFalse;
            }
        });
        binding.setProperty("AND", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object... args) {
                for (Object arg : args) {
                    if (!Boolean.TRUE.equals(arg)) {
                        return false;
                    }
                }
                return true;
            }
        });
        binding.setProperty("OR", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object... args) {
                for (Object arg : args) {
                    if (Boolean.TRUE.equals(arg)) {
                        return true;
                    }
                }
                return false;
            }
        });
        binding.setProperty("NOT", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object condition) {
                return !Boolean.TRUE.equals(condition);
            }
        });
        binding.setProperty("ISBLANK", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return value == null || String.valueOf(value).trim().isEmpty();
            }
        });
        binding.setProperty("NUMBER", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return value == null || String.valueOf(value).trim().isEmpty()
                        ? null : toNumber(value, "NUMBER");
            }
        });
        binding.setProperty("TEXT", (Closure<Object>) new Closure<Object>(null) {
            public Object doCall(Object value) {
                return value == null ? "" : String.valueOf(value);
            }
        });
        // HAS_ROLE 读发起人角色名集合（enrichGatewayVars 注入的 __initiator_roles；老实例可能没有，按无角色处理）
        Object roles = binding.hasVariable(INITIATOR_ROLES) ? binding.getVariable(INITIATOR_ROLES) : null;
        binding.setProperty("HAS_ROLE", new HasRoleClosure(roles));
    }

    private Closure<Object> sumClosure() {
        return new Closure<Object>(null) {
            public Object doCall(Object... args) {
                double sum = 0;
                for (Object arg : args) {
                    if (arg != null && !String.valueOf(arg).trim().isEmpty()) {
                        sum += toNumber(arg, "SUM");
                    }
                }
                return sum;
            }
        };
    }

    private Closure<Object> extremumClosure(boolean max) {
        return new Closure<Object>(null) {
            public Object doCall(Object... args) {
                Double best = null;
                for (Object arg : args) {
                    if (arg == null || String.valueOf(arg).trim().isEmpty()) {
                        continue;
                    }
                    double value = toNumber(arg, max ? "MAX" : "MIN");
                    best = best == null ? value : (max ? Math.max(best, value) : Math.min(best, value));
                }
                return best;
            }
        };
    }

    private Closure<Object> datePartClosure(String part) {
        return new Closure<Object>(null) {
            public Object doCall(Object value) {
                Date date = toDate(value, part.toUpperCase());
                return switch (part) {
                    case "year" -> DateUtil.date(date).year();
                    case "month" -> DateUtil.date(date).month() + 1;
                    default -> DateUtil.date(date).dayOfMonth();
                };
            }
        };
    }

    private static class HasRoleClosure extends Closure<Object> {
        private final Collection<?> roleNames;

        HasRoleClosure(Object roleNames) {
            super(null);
            this.roleNames = roleNames instanceof Collection ? (Collection<?>) roleNames : Collections.emptyList();
        }

        public Object doCall(Object roleName) {
            return roleName != null && roleNames.contains(String.valueOf(roleName));
        }
    }

    private double toNumber(Object value, String function) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return new BigDecimal(String.valueOf(value).replace(",", "").trim()).doubleValue();
        } catch (Exception e) {
            throw new FlowException(function + "() 的参数不是数值：" + value);
        }
    }

    private Date toDate(Object value, String function) {
        if (value instanceof Date date) {
            return date;
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new FlowException(function + "() 的日期参数为空");
        }
        try {
            return DateUtil.parse(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new FlowException(function + "() 无法解析日期：" + value);
        }
    }

    /** 编译缓存 + 独立线程池执行，超时中断（ThreadInterrupt AST 在循环处插入中断检查） */
    private Object run(String script, Binding binding) {
        Class<?> scriptClass;
        synchronized (scriptCache) {
            scriptClass = scriptCache.get(script);
            if (scriptClass == null) {
                scriptClass = shell.parse(script).getClass();
                scriptCache.put(script, scriptClass);
            }
        }
        groovy.lang.Script instance;
        try {
            instance = (groovy.lang.Script) scriptClass.getDeclaredConstructor(Binding.class).newInstance(binding);
        } catch (ReflectiveOperationException e) {
            throw new FlowException("流转条件表达式编译失败：" + e.getMessage());
        }
        Future<Object> future = evalExecutor.submit((Callable<Object>) instance::run);
        try {
            return future.get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new FlowException("流转条件表达式执行超时（" + EVAL_TIMEOUT_SECONDS + " 秒），已中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error("网关流转条件求值失败 script={}", script, cause);
            throw new FlowException("流转条件表达式执行失败：" + rootMessage(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlowException("流转条件表达式执行被中断");
        }
    }

    private String rootMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return StringUtils.isEmpty(message) ? throwable.getClass().getSimpleName() : message;
    }
}
