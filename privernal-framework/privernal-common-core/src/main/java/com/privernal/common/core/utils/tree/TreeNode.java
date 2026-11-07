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

import java.util.List;

/**
 * 节点接口:
 * E:接口的实现类类型
 * T:id的类型
 */
public interface TreeNode<E, T> {
    // 此节点的Id
    T getId();

    // 此节点的父节点Id
    T getParentId();

    // 设置此节点的子节点列表
    void setChildren(List<E> treeNodeList);

    // 获取此节点的子节点列表
    List<E> getChildren();

    /**
     * 获取排序的值
     *
     * @return 排序的值
     */
    Comparable<?> getOrderNum();
}
