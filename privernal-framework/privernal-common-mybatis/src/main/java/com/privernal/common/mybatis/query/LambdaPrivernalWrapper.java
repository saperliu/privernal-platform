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

package com.privernal.common.mybatis.query;

import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * 拓展 MyBatis Plus QueryWrapper 类，主要增加如下功能：
 * <p>
 * 1. 拼接条件的方法，增加 xxxIfPresent 方法，用于判断值不存在的时候，不要拼接到条件中。
 *
 * @param <T> 数据类型
 */
public class LambdaPrivernalWrapper<T> extends LambdaQueryWrapper<T> {

    public LambdaPrivernalWrapper<T> likeIfPresent(SFunction<T, ?> column, String val) {
        if (StringUtils.hasText(val)) {
            return (LambdaPrivernalWrapper<T>) super.like(column, val);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> inIfPresent(SFunction<T, ?> column, Collection<?> values) {
        if (!CollectionUtils.isEmpty(values)) {
            return (LambdaPrivernalWrapper<T>) super.in(column, values);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> inIfPresent(SFunction<T, ?> column, Object... values) {
        if (!ArrayUtil.isEmpty(values)) {
            return (LambdaPrivernalWrapper<T>) super.in(column, values);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaPrivernalWrapper<T>) super.eq(column, val);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> neIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaPrivernalWrapper<T>) super.ne(column, val);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> gtIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaPrivernalWrapper<T>) super.gt(column, val);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> geIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaPrivernalWrapper<T>) super.ge(column, val);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> ltIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaPrivernalWrapper<T>) super.lt(column, val);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> leIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaPrivernalWrapper<T>) super.le(column, val);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> betweenIfPresent(SFunction<T, ?> column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            return (LambdaPrivernalWrapper<T>) super.between(column, val1, val2);
        }
        if (val1 != null) {
            return (LambdaPrivernalWrapper<T>) ge(column, val1);
        }
        if (val2 != null) {
            return (LambdaPrivernalWrapper<T>) le(column, val2);
        }
        return this;
    }

    public LambdaPrivernalWrapper<T> betweenIfPresent(SFunction<T, ?> column, Object[] values) {
        Object val1 = ArrayUtils.get(values, 0);
        Object val2 = ArrayUtils.get(values, 1);
        return betweenIfPresent(column, val1, val2);
    }

    // ========== 重写父类方法，方便链式调用 ==========

    @Override
    public LambdaPrivernalWrapper<T> eq(boolean condition, SFunction<T, ?> column, Object val) {
        super.eq(condition, column, val);
        return this;
    }

    @Override
    public LambdaPrivernalWrapper<T> eq(SFunction<T, ?> column, Object val) {
        super.eq(column, val);
        return this;
    }

    @Override
    public LambdaPrivernalWrapper<T> orderByDesc(SFunction<T, ?> column) {
        super.orderByDesc(true, column);
        return this;
    }

    @Override
    public LambdaPrivernalWrapper<T> last(String lastSql) {
        super.last(lastSql);
        return this;
    }

    @Override
    public LambdaPrivernalWrapper<T> in(SFunction<T, ?> column, Collection<?> coll) {
        super.in(column, coll);
        return this;
    }

    @Override
    public LambdaPrivernalWrapper<T> or(Consumer<LambdaQueryWrapper<T>> consumer) {
        super.or(consumer);
        return this;
    }

    @Override
    public LambdaPrivernalWrapper<T> or() {
        super.or();
        return this;
    }

    @Override
    public LambdaPrivernalWrapper<T> and(Consumer<LambdaQueryWrapper<T>> consumer) {
        super.and(consumer);
        return this;
    }


}
