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

package com.privernal.common.mybatis.mapper;

import com.privernal.common.web.domain.PageListDTO;
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.web.domain.FilterDTO;
import com.privernal.common.web.page.SortOrderField;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 扩展 mybatis plus 的基础
 * 增加角色权限的判断
 *
 * @param <T>
 */
public interface PrivernalMapper<T> extends BaseMapper<T> {
    @Override
    default List<T> selectByMap(Map<String, Object> columnMap) {
        return BaseMapper.super.selectByMap(columnMap);
    }

    @Override
    default T selectOne(@Param("ew") Wrapper<T> queryWrapper) {
        return BaseMapper.super.selectOne(queryWrapper);
    }

    @Override
    default T selectOne(@Param("ew") Wrapper<T> queryWrapper, boolean throwEx) {
        return BaseMapper.super.selectOne(queryWrapper, throwEx);
    }

    @Override
    default boolean exists(@Param("ew") Wrapper<T> queryWrapper) {
        return BaseMapper.super.exists(queryWrapper);
    }

    @Override
    default <P extends IPage<T>> P selectPage(P page, @Param("ew") Wrapper<T> queryWrapper) {
        return BaseMapper.super.selectPage(page, queryWrapper);
    }

    @Override
    default <P extends IPage<Map<String, Object>>> P selectMapsPage(P page, @Param("ew") Wrapper<T> queryWrapper) {
        return BaseMapper.super.selectMapsPage(page, queryWrapper);
    }

    @Override
    T selectById(Serializable id);

    @Override
    List<T> selectBatchIds(@Param("coll") Collection<? extends Serializable> idList);

    @Override
    Long selectCount(@Param("ew") Wrapper<T> queryWrapper);

    @Override
    List<T> selectList(@Param("ew") Wrapper<T> queryWrapper);

    @Override
    List<T> selectList(IPage<T> page, @Param("ew") Wrapper<T> queryWrapper);

    @Override
    List<Map<String, Object>> selectMaps(@Param("ew") Wrapper<T> queryWrapper);


    @Override
    List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page, @Param("ew") Wrapper<T> queryWrapper);


    @Override
    <E> List<E> selectObjs(Wrapper<T> queryWrapper);

    int deleteFillById(Serializable id);

    default PageListDTO<T> selectPage(FilterDTO filterDTO, @Param("ew") Wrapper<T> queryWrapper) {
        // MyBatis Plus 查询
        IPage<T> mpPage = buildPage(filterDTO);
        selectPage(mpPage, queryWrapper);
        // 转换返回
        return new PageListDTO<>(mpPage.getRecords(), mpPage.getTotal());
    }

    default T selectOne(String field, Object value) {
        return selectOne(new QueryWrapper<T>().eq(field, value));
    }

    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default T selectOne(String field1, Object value1, String field2, Object value2) {
        return selectOne(new QueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default Long selectCount(String field, Object value) {
        return selectCount(new QueryWrapper<T>().eq(field, value));
    }

    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    default List<T> selectList(String field, Object value) {
        return selectList(new QueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(SFunction<T, ?> field, Object value,SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value).eq(field2, value2));
    }

    default List<T> selectList(String field, Collection<?> values) {
        return selectList(new QueryWrapper<T>().in(field, values));
    }

    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    default List<T> selectList(SFunction<T, ?> leField, SFunction<T, ?> geField, Object value) {
        return selectList(new LambdaQueryWrapper<T>().le(leField, value).ge(geField, value));
    }

    default void updateBatch(T update) {
        update(update, new QueryWrapper<>());
    }

    @Override
    void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<T> resultHandler);

    @Override
    default void selectByMap(Map<String, Object> columnMap, ResultHandler<T> resultHandler) {
        BaseMapper.super.selectByMap(columnMap, resultHandler);
    }

    @Override
    void selectList(Wrapper<T> queryWrapper, ResultHandler<T> resultHandler);

    @Override
    void selectList(IPage<T> page, Wrapper<T> queryWrapper, ResultHandler<T> resultHandler);

    @Override
    void selectMaps(Wrapper<T> queryWrapper, ResultHandler<Map<String, Object>> resultHandler);

    @Override
    void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<T> queryWrapper, ResultHandler<Map<String, Object>> resultHandler);

    @Override
    <E> void selectObjs(Wrapper<T> queryWrapper, ResultHandler<E> resultHandler);


    public static <T> Page<T> buildPage(FilterDTO pageParam) {
        return buildPage(pageParam, null);
    }

    public static <T> Page<T> buildPage(FilterDTO pageParam, Collection<SortOrderField> sortingFields) {
        // 页码 + 数量
        Page<T> page = new Page<T>(pageParam.getPageNum(), pageParam.getPageSize());
        // 排序字段
        if (!CollectionUtil.isEmpty(sortingFields)) {
            page.addOrder(sortingFields.stream().map(sortingField -> SortOrderField.ORDER_ASC.equals(sortingField.getOrder()) ?
                            OrderItem.asc(sortingField.getField()) : OrderItem.desc(sortingField.getField()))
                    .collect(Collectors.toList()));
        }
        return page;
    }

    /**
     * 批量插入
     *
     * @param entityList
     * @return 条数
     */
    int insertBatchSomeColumn(List<T> entityList);
}
