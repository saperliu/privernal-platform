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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 树装配的行为基线。
 *
 * <p>覆盖两条路径：不配别名时走接口方法，配了别名时走 JSON 反射取值。
 * 这两条路径过去对「排序值为 null」的处理不一致——默认路径有保护、别名路径直接 NPE，
 * 而菜单树正是配了别名的那一类调用。</p>
 */
class TreeUtilsTest
{
    @Test
    @DisplayName("空集合与 null 输入原样返回")
    void emptyInputPassesThrough()
    {
        assertNull(TreeUtils.convertToTree(null));
        List<Node> none = new ArrayList<>();
        assertEquals(none, TreeUtils.convertToTree(none));
    }

    @Test
    @DisplayName("默认命名：三层结构按 parentId 挂接")
    void buildsThreeLevelTree()
    {
        List<Node> flat = Arrays.asList(
                node("1", null, 1),
                node("1-1", "1", 1),
                node("1-1-1", "1-1", 1));

        List<Node> tree = TreeUtils.convertToTree(flat);

        assertEquals(1, tree.size(), "只有一个根节点");
        assertEquals("1", tree.get(0).getId());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("1-1", tree.get(0).getChildren().get(0).getId());
        assertEquals("1-1-1", tree.get(0).getChildren().get(0).getChildren().get(0).getId());
    }

    @Test
    @DisplayName("父节点不在列表里的节点算作顶层")
    void orphanParentsBecomeRoots()
    {
        List<Node> flat = Arrays.asList(
                node("2", "not-in-list", 1),
                node("3", "not-in-list", 2));

        List<Node> tree = TreeUtils.convertToTree(flat);
        assertEquals(2, tree.size());
    }

    @Test
    @DisplayName("指定根节点时只返回该节点下的子列表")
    void returnsSubTreeForGivenRoot()
    {
        List<Node> flat = Arrays.asList(
                node("root", null, 1),
                node("a", "root", 2),
                node("b", "root", 1));

        List<Node> sub = TreeUtils.convertToTree(flat, "root");
        assertEquals(2, sub.size());
    }

    @Test
    @DisplayName("同层按排序值降序；排序值为 null 不得抛异常")
    void sortsDescendingAndToleratesNullOrder()
    {
        List<Node> flat = new ArrayList<>(Arrays.asList(
                node("1", null, 10),
                node("low", "1", 1),
                node("nul", "1", null),
                node("high", "1", 99)));

        List<Node> tree = TreeUtils.convertToTree(flat);
        List<Node> children = tree.get(0).getChildren();
        assertEquals(3, children.size());
        assertEquals("high", children.get(0).getId(), "99 应排最前");
        assertEquals("low", children.get(1).getId(), "1 排第二");
        assertEquals("nul", children.get(2).getId(), "null 排最后");
    }

    @Test
    @DisplayName("别名字段成树：id 键换成 menuId")
    void buildsTreeWithAliasedKeys()
    {
        TreeNodeConfig config = new TreeNodeConfig();
        config.setIdKey("menuId");

        List<AliasNode> flat = new ArrayList<>(Arrays.asList(
                alias("m", null, null),
                alias("m-a", "m", 5),
                alias("m-b", "m", null),
                alias("m-c", "m", 8)));

        List<AliasNode> tree = TreeUtils.convertToTree(flat, config, null);

        assertEquals(1, tree.size(), "只有 m 是顶层");
        List<AliasNode> children = tree.get(0).getChildrenList();
        assertEquals(3, children.size());
        assertEquals("m-c", children.get(0).getMenuId(), "8 最大应排最前");
        assertEquals("m-a", children.get(1).getMenuId());
    }

    @Test
    @DisplayName("单节点无父无子不抛异常")
    void singleNodeIsSafe()
    {
        List<Node> tree = TreeUtils.convertToTree(new ArrayList<>(Collections.singletonList(node("only", null, 1))));
        assertEquals(1, tree.size());
    }

    private static Node node(String id, String parentId, Integer order)
    {
        Node n = new Node();
        n.setId(id);
        n.setParentId(parentId);
        n.setName(id);
        n.setOrderNum(order);
        return n;
    }

    private static AliasNode alias(String menuId, String pId, Integer order)
    {
        AliasNode n = new AliasNode();
        n.setMenuId(menuId);
        n.setParentId(pId);
        n.setOrderNum(order);
        return n;
    }

    /**
     * 字段名与默认约定完全不同的节点类型，用于验证别名配置路径
     */
    @Data
    public static class AliasNode implements TreeNode<AliasNode, String>
    {
        private String menuId;
        private String parentId;
        private Integer orderNum;
        private List<AliasNode> childrenList = new ArrayList<>();

        @Override
        public String getId()
        {
            return menuId;
        }

        @Override
        public String getParentId()
        {
            return parentId;
        }

        @Override
        public List<AliasNode> getChildren()
        {
            return childrenList;
        }

        @Override
        public void setChildren(List<AliasNode> treeNodeList)
        {
            this.childrenList = treeNodeList;
        }

        @Override
        public Comparable<?> getOrderNum()
        {
            return orderNum;
        }
    }
}
