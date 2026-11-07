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

import com.privernal.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 树型数据的组织
 *
 * @author saperliu
 * <p>
 * <p>用法：节点类型实现 {@link TreeNode}（提供 id、parentId、children 的读写与排序值），
 * 然后 {@code TreeUtils.convertToTree(list)} 即可；传入 {@link TreeNodeConfig} 时，
 * id 与 parentId 改按配置的属性名从序列化结果里取，适用于字段名与默认约定不同的场景。</p>
 *
 * <p>装配是<b>就地</b>完成的：传入的每个节点对象会被 setChildren 挂上自己的子列表，
 * 返回的是顶层节点组成的新列表，而不是重新构造的节点树。父节点不在集合里的节点视为顶层。</p>
 */
@Slf4j
public class TreeUtils {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareOrderNum(Comparable a, Comparable b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    /**
     * @param treeNodeList 要组织成树型的数据集合
     * @param config       属性名映射；传非 null 即启用按 JSON 属性名取值
     * @param rootNode     顶层节点的 id；传值则只返回它的直接子列表
     * @param descend      同层排序方向，true 降序；排序值为 null 的节点一律排最后
     * @param <T>          节点ID的类型
     * @param <E>          树型的数据类型
     * @return 顶层节点列表（节点对象本身已被就地挂上子列表）
     */
    public static <T, E extends TreeNode> List<E> convertToTree(List<E> treeNodeList, TreeNodeConfig config, T rootNode, boolean descend) {

        if (treeNodeList == null || treeNodeList.size() == 0) {
            return treeNodeList;
        }
        //存储列表里的父节点不存的子节点。
        Map<T, List<E>> nodeIdMap = new HashMap<>();
        for (TreeNode<E, T> treeNode : treeNodeList) {
            if (config != null && StringUtils.isNotEmpty(config.getIdKey())) {
                JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(treeNode));
                T pId = (T) jsonObject.get(config.getIdKey());
                if (!nodeIdMap.containsKey(pId)) {
                    nodeIdMap.put(pId, null);
                }
            } else {
                T id = treeNode.getId();
                if (!nodeIdMap.containsKey(id)) {
                    nodeIdMap.put(id, null);
                }
            }
        }

        Map<T, TreeNode<E, T>> topNodeMap = new HashMap<>();
        //将父节点相同的节点分组
        Map<T, List<E>> map = new HashMap<>();
        for (TreeNode<E, T> treeNode : treeNodeList) {
            if (config != null && StringUtils.isNotEmpty(config.getParentIdKey())) {
                JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(treeNode));
                T pId = (T) jsonObject.get(config.getParentIdKey());
                if (!map.containsKey(pId)) {
                    map.put(pId, new ArrayList<>());
                }
                if (!nodeIdMap.containsKey(pId)) {
                    //此节点的父节点不在当前列表里。需要单独加到list里
                    topNodeMap.put(pId, treeNode);
                }
                map.get(pId).add((E) treeNode);

            } else {
                T pId = treeNode.getParentId();
                if (!map.containsKey(pId)) {
                    map.put(pId, new ArrayList<>());
                }
                if (!nodeIdMap.containsKey(pId)) {
                    //此节点的父节点不在当前列表里。需要单独加到list里
                    topNodeMap.put(pId, treeNode);
                }
                map.get(pId).add((E) treeNode);
            }
        }
        //遍历map生成Tree
        // 传了 config 就按别名取值：idKey 有默认值，因此这里不能用 idKey 是否为空来区分模式，
        // 但只要 config 非空就走 JSON 取属性，与既有调用方的预期一致
        boolean aliased = useAliases(config);
        Map<Object, Comparable<?>> orderNums = aliased ? collectOrderNums(treeNodeList, config) : null;
        Comparator<Object> byOrderNum = nodeComparator(config, orderNums, descend);

        for (Map.Entry<T, List<E>> entry : map.entrySet()) {
            List<E> child = entry.getValue();
            if (child == null || child.isEmpty()) {
                continue;
            }
            for (E childNode : child) {
                TreeNode<E, T> node = (TreeNode<E, T>) childNode;
                T id = aliased ? idOf(childNode, config) : node.getId();
                node.setChildren(map.get(id));
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    sortNodes(node.getChildren(), byOrderNum);
                }
            }
        }

        // 一种是有固定的顶级节点，一种是没有固定的顶级节点(未知)
        if (StringUtils.isNotNull(rootNode)) {
            return map.get(rootNode);
        }
        List<E> list = new ArrayList<>();
        for (T key : topNodeMap.keySet()) {
            // 说明是顶级节点，取此节点
            List<E> child = map.get(key);
            if (child != null) {
                list.addAll(child);
            }
        }
        sortNodes(list, byOrderNum);
        return list;
    }

    private static boolean useAliases(TreeNodeConfig config)
    {
        return config != null && StringUtils.isNotEmpty(config.getIdKey());
    }

    @SuppressWarnings("unchecked")
    private static <T> T idOf(Object node, TreeNodeConfig config)
    {
        return (T) JSONObject.parseObject(JSON.toJSONString(node)).get(config.getIdKey());
    }

    /**
     * 每个节点的排序值只提取一次。原实现在比较器里反复序列化整个节点对象，
     * 排序 n 个节点要序列化 O(n log n) 次
     */
    private static Map<Object, Comparable<?>> collectOrderNums(List<?> nodes, TreeNodeConfig config)
    {
        Map<Object, Comparable<?>> orderNums = new IdentityHashMap<>();
        for (Object node : nodes)
        {
            if (node == null || orderNums.containsKey(node))
            {
                continue;
            }
            Object value = JSONObject.parseObject(JSON.toJSONString(node)).get(config.getOrderNumKey());
            orderNums.put(node, value instanceof Comparable ? (Comparable<?>) value : null);
        }
        return orderNums;
    }

    /**
     * 用身份映射而非 equals：节点对象的 equals 可能按字段全等，两个同内容节点会互相覆盖
     */
    private static Comparable<?> orderNumOf(Object node, TreeNodeConfig config, Map<Object, Comparable<?>> orderNums)
    {
        if (orderNums != null)
        {
            return orderNums.get(node);
        }
        return node instanceof TreeNode ? ((TreeNode<?, ?>) node).getOrderNum() : null;
    }

    private static Comparator<Object> nodeComparator(TreeNodeConfig config, Map<Object, Comparable<?>> orderNums, boolean descend)
    {
        return (a, b) ->
        {
            Comparable<?> first = orderNumOf(a, config, orderNums);
            Comparable<?> second = orderNumOf(b, config, orderNums);
            return descend ? compareOrderNum(second, first) : compareOrderNum(first, second);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void sortNodes(List nodes, Comparator<Object> comparator)
    {
        nodes.sort(comparator);
    }

    public static <T, E extends TreeNode> List<E> convertToTree(List<E> treeNodeList) {
        return convertToTree(treeNodeList, null, null, true);
    }

    public static <T, E extends TreeNode> List<E> convertToTree(List<E> treeNodeList, T rootNode) {
        return convertToTree(treeNodeList, null, rootNode, true);
    }

    public static <T, E extends TreeNode> List<E> convertToTree(List<E> treeNodeList, T rootNode,
                                                                boolean descend) {
        return convertToTree(treeNodeList, null, rootNode, descend);
    }

    public static <T, E extends
            TreeNode> List<E> convertToTree(List<E> treeNodeList, TreeNodeConfig config, T rootNode) {
        return convertToTree(treeNodeList, config, rootNode, true);
    }


    public static <T, E extends TreeNode> List<E> convertToTree(List<E> treeNodeList, TreeNodeConfig config,
                                                                boolean descend) {
        return convertToTree(treeNodeList, config, null, descend);
    }

}
