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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ArrayUtils;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 拓展 MyBatis Plus QueryWrapper 类，主要增加如下功能：
 *
 * 1. 拼接条件的方法，增加 xxxIfPresent 方法，用于判断值不存在的时候，不要拼接到条件中。
 *
 * @param <T> 数据类型
 */
public class PrivernalWrapper<T> extends QueryWrapper<T> {

    public PrivernalWrapper<T> likeIfPresent(String column, String val) {
        if (StringUtils.hasText(val)) {
            return (PrivernalWrapper<T>) super.like(column, val);
        }
        return this;
    }

    public PrivernalWrapper<T> inIfPresent(String column, Collection<?> values) {
        if (!CollectionUtils.isEmpty(values)) {
            return (PrivernalWrapper<T>) super.in(column, values);
        }
        return this;
    }

    public PrivernalWrapper<T> inIfPresent(String column, Object... values) {
        if (!ArrayUtils.isEmpty(values)) {
            return (PrivernalWrapper<T>) super.in(column, values);
        }
        return this;
    }

    public PrivernalWrapper<T> eqIfPresent(String column, Object val) {
        if (val != null) {
            return (PrivernalWrapper<T>) super.eq(column, val);
        }
        return this;
    }

    public PrivernalWrapper<T> neIfPresent(String column, Object val) {
        if (val != null) {
            return (PrivernalWrapper<T>) super.ne(column, val);
        }
        return this;
    }

    public PrivernalWrapper<T> gtIfPresent(String column, Object val) {
        if (val != null) {
            return (PrivernalWrapper<T>) super.gt(column, val);
        }
        return this;
    }

    public PrivernalWrapper<T> geIfPresent(String column, Object val) {
        if (val != null) {
            return (PrivernalWrapper<T>) super.ge(column, val);
        }
        return this;
    }

    public PrivernalWrapper<T> ltIfPresent(String column, Object val) {
        if (val != null) {
            return (PrivernalWrapper<T>) super.lt(column, val);
        }
        return this;
    }

    public PrivernalWrapper<T> leIfPresent(String column, Object val) {
        if (val != null) {
            return (PrivernalWrapper<T>) super.le(column, val);
        }
        return this;
    }

    public PrivernalWrapper<T> betweenIfPresent(String column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            return (PrivernalWrapper<T>) super.between(column, val1, val2);
        }
        if (val1 != null) {
            return (PrivernalWrapper<T>) ge(column, val1);
        }
        if (val2 != null) {
            return (PrivernalWrapper<T>) le(column, val2);
        }
        return this;
    }

    public PrivernalWrapper<T> betweenIfPresent(String column, Object[] values) {
        if (values!= null && values.length != 0 && values[0] != null && values[1] != null) {
            return (PrivernalWrapper<T>) super.between(column, values[0], values[1]);
        }
        if (values!= null && values.length != 0 && values[0] != null) {
            return (PrivernalWrapper<T>) ge(column, values[0]);
        }
        if (values!= null && values.length != 0 && values[1] != null) {
            return (PrivernalWrapper<T>) le(column, values[1]);
        }
        return this;
    }

    // ========== 重写父类方法，方便链式调用 ==========

    @Override
    public PrivernalWrapper<T> eq(boolean condition, String column, Object val) {
        super.eq(condition, column, val);
        return this;
    }

    @Override
    public PrivernalWrapper<T> eq(String column, Object val) {
        super.eq(column, val);
        return this;
    }

    @Override
    public PrivernalWrapper<T> orderByDesc(String column) {
        super.orderByDesc(true, column);
        return this;
    }

    @Override
    public PrivernalWrapper<T> last(String lastSql) {
        super.last(lastSql);
        return this;
    }

    @Override
    public PrivernalWrapper<T> in(String column, Collection<?> coll) {
        super.in(column, coll);
        return this;
    }

}
