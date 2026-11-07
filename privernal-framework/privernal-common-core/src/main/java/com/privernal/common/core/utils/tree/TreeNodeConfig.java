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

package com.privernal.common.core.utils.tree;

import java.io.Serializable;

/**
 * {@link TreeUtils} 的属性名映射配置。
 *
 * <p>只有三个键真正参与装配：{@code idKey}、{@code parentIdKey}、{@code orderNumKey}。
 * 子节点列表不在此配置——它通过 {@link TreeNode#setChildren(java.util.List)} 挂接，
 * 节点类型必须实现该接口。</p>
 *
 * <p>⚠️ 别名是按 <em>序列化后的 JSON 属性名</em> 匹配的，而不是按 Java 字段名。
 * 二者对 {@code pId} 这类「单个小写字母 + 大写字母」的字段并不相同
 * （getter 推导出的属性名是 {@code PId}），配了取不到值、且不会报错，
 * 只表现为整棵树被平铺成顶层节点。给这类字段起别名前先确认序列化结果。</p>
 *
 * @author saperliu
 */
public class TreeNodeConfig implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String idKey = "id";

    private String parentIdKey = "parentId";

    private String orderNumKey = "orderNum";

    public String getIdKey()
    {
        return idKey;
    }

    public TreeNodeConfig setIdKey(String idKey)
    {
        this.idKey = idKey;
        return this;
    }

    public String getParentIdKey()
    {
        return parentIdKey;
    }

    public TreeNodeConfig setParentIdKey(String parentIdKey)
    {
        this.parentIdKey = parentIdKey;
        return this;
    }

    public String getOrderNumKey()
    {
        return orderNumKey;
    }

    public TreeNodeConfig setOrderNumKey(String orderNumKey)
    {
        this.orderNumKey = orderNumKey;
        return this;
    }
}
