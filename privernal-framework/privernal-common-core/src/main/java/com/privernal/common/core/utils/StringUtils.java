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

package com.privernal.common.core.utils;


import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.util.AntPathMatcher;

/**
 * 字符串工具类
 *
 * @author saperliu
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils
{

    private static final Pattern PATTERN_XML_NCNAME = Pattern.compile("[a-zA-Z_][\\-_.0-9_a-zA-Z$]*");

    /** 空字符串 */
    private static final String NULLSTR = "";

    /** 下划线 */
    private static final char SEPARATOR = '_';

    /**
     * * 判断一个Collection是否为空， 包含List，Set，Queue
     *
     * @param coll 要判断的Collection
     * @return true：为空 false：非空
     */
    public static boolean isEmpty(Collection<?> coll)
    {
        return isNull(coll) || coll.isEmpty();
    }

    /**
     * * 判断一个Collection是否非空，包含List，Set，Queue
     *
     * @param coll 要判断的Collection
     * @return true：非空 false：空
     */
    public static boolean isNotEmpty(Collection<?> coll)
    {
        return !isEmpty(coll);
    }

    /**
     * * 判断一个对象数组是否为空
     *
     * @param objects 要判断的对象数组
     ** @return true：为空 false：非空
     */
    public static boolean isEmpty(Object[] objects)
    {
        return isNull(objects) || (objects.length == 0);
    }

    /**
     * * 判断一个对象数组是否非空
     *
     * @param objects 要判断的对象数组
     * @return true：非空 false：空
     */
    public static boolean isNotEmpty(Object[] objects)
    {
        return !isEmpty(objects);
    }

    /**
     * * 判断一个Map是否为空
     *
     * @param map 要判断的Map
     * @return true：为空 false：非空
     */
    public static boolean isEmpty(Map<?, ?> map)
    {
        return isNull(map) || map.isEmpty();
    }

    /**
     * * 判断一个Map是否为空
     *
     * @param map 要判断的Map
     * @return true：非空 false：空
     */
    public static boolean isNotEmpty(Map<?, ?> map)
    {
        return !isEmpty(map);
    }

    /**
     * * 判断一个字符串是否为空串
     *
     * @param str String
     * @return true：为空 false：非空
     */
    /**
     * 截取子串，负索引从尾部算、越界夹到边界。
     *
     * <p>算法交给 commons-lang3，这里只补它的一处差异：入参为 null 时它返回 null，
     * 而本方法历来返回空串——已有调用方依赖这个约定，故在此归一化。</p>
     */
    public static String substring(final String str, int start)
    {
        String sliced = org.apache.commons.lang3.StringUtils.substring(str, start);
        return sliced == null ? NULLSTR : sliced;
    }

    /**
     * 截取子串，end 为开区间；start 大于 end 时返回空串。
     */
    public static String substring(final String str, int start, int end)
    {
        String sliced = org.apache.commons.lang3.StringUtils.substring(str, start, end);
        return sliced == null ? NULLSTR : sliced;
    }

    public static boolean isEmpty(String str)
    {
        // 注意：这里要的是 isBlank 语义（纯空白算空），不是 commons-lang3 的 isEmpty
        return org.apache.commons.lang3.StringUtils.isBlank(str);
    }

    /**
     * * 判断一个字符串是否为非空串
     *
     * @param str String
     * @return true：非空串 false：空串
     */
    public static boolean isNotEmpty(String str)
    {
        return !isEmpty(str);
    }

    /**
     * * 判断一个对象是否为空
     *
     * @param object Object
     * @return true：为空 false：非空
     */
    public static boolean isNull(Object object)
    {
        return object == null;
    }

    /**
     * * 判断一个对象是否非空
     *
     * @param object Object
     * @return true：非空 false：空
     */
    public static boolean isNotNull(Object object)
    {
        return !isNull(object);
    }



    /**
     * 是否有文本：非 null、非空且至少含一个非空白字符。
     *
     * <p>直接采用 Spring 的实现，避免在这里维护一份同义逻辑。</p>
     */
    public static boolean hasText(String str)
    {
        return org.springframework.util.StringUtils.hasText(str);
    }


    /**
     * 格式化文本, {} 表示占位符<br>
     * 此方法只是简单将占位符 {} 按照顺序替换为参数<br>
     * 如果想输出 {} 使用 \\转义 { 即可，如果想输出 {} 之前的 \ 使用双转义符 \\\\ 即可<br>
     * 例：<br>
     * 通常使用：format("this is {} for {}", "a", "b") -> this is a for b<br>
     * 转义{}： format("this is \\{} for {}", "a", "b") -> this is \{} for a<br>
     * 转义\： format("this is \\\\{} for {}", "a", "b") -> this is \a for b<br>
     *
     * @param template 文本模板，被替换的部分用 {} 表示
     * @param params 参数值
     * @return 格式化后的文本
     */
    public static String format(String template, Object... params)
    {
        if (template == null || template.isEmpty() || params == null || params.length == 0)
        {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + 16 * params.length);
        int cursor = 0;
        int argIndex = 0;
        while (argIndex < params.length)
        {
            int at = template.indexOf("{}", cursor);
            if (at < 0)
            {
                break;
            }
            boolean precededByBackslash = at > 0 && template.charAt(at - 1) == '\\';
            boolean backslashEscaped = precededByBackslash && at > 1 && template.charAt(at - 2) == '\\';
            if (precededByBackslash && !backslashEscaped)
            {
                // \{} 是字面量 {}：吃掉反斜杠，占位符不生效，也不消耗参数
                sb.append(template, cursor, at - 1).append('{');
                cursor = at + 1;
                continue;
            }
            // 双反斜杠时保留一个反斜杠，占位符照常生效
            sb.append(template, cursor, backslashEscaped ? at - 1 : at);
            sb.append(toDisplayString(params[argIndex++]));
            cursor = at + 2;
        }
        sb.append(template, cursor, template.length());
        return sb.toString();
    }

    /**
     * 驼峰转下划线命名（列名风格）。
     *
     * <p>只在「大写转小写」的交界处插分隔符：连续大写缩略词视作一个词
     * （{@code HTTPSUrl} → {@code https_url}，{@code USERNAME} → {@code username}），
     * 小写或数字后的大写是新词起点（{@code userId} → {@code user_id}）。</p>
     */
    public static String toUnderScoreCase(String str)
    {
        if (str == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder(str.length() + 4);
        for (int i = 0; i < str.length(); i++)
        {
            char curr = str.charAt(i);
            boolean currUpper = Character.isUpperCase(curr);
            boolean prevUpper = i > 0 && Character.isUpperCase(str.charAt(i - 1));
            boolean nextLower = i + 1 < str.length() && !Character.isUpperCase(str.charAt(i + 1));
            if (currUpper && i > 0 && ((prevUpper && nextLower) || !prevUpper))
            {
                sb.append(SEPARATOR);
            }
            sb.append(Character.toLowerCase(curr));
        }
        return sb.toString();
    }


    /**
     * 模板参数转展示字符串。
     *
     * <p>null 交给调用方的 StringBuilder 输出 "null"；字节数组按 UTF-8 解码；
     * 对象数组用 Arrays.toString 而非默认的 "[Ljava.lang.Object;@..."。</p>
     */
    private static String toDisplayString(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof String)
        {
            return (String) value;
        }
        if (value instanceof byte[])
        {
            return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (value instanceof Byte[])
        {
            return new String(toBytes((Byte[]) value), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (value instanceof Object[])
        {
            return java.util.Arrays.toString((Object[]) value);
        }
        return String.valueOf(value);
    }

    private static byte[] toBytes(Byte[] boxed)
    {
        byte[] raw = new byte[boxed.length];
        for (int i = 0; i < boxed.length; i++)
        {
            raw[i] = boxed[i] == null ? 0 : boxed[i];
        }
        return raw;
    }

}
