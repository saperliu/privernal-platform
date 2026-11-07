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

import lombok.Data;

import java.util.List;

@Data
public class Node implements TreeNode<Node,String>{
    private String id;
    private String name;
    private String parentId;
    private Integer orderNum;
    private List<Node> children;

    @Override
    public Comparable<?> getOrderNum() {
        return orderNum;
    }
}