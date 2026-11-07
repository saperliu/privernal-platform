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

package com.privernal.common.datasource;


import cn.hutool.core.date.DateUtil;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.*;

/**
 * @author saperliu
 * @ClassName 分库策略配置：按照年 如user_log2021
 * tableDateShardingAlgorithm 名称配置到
 * table-strategy：
 * standard:
 * sharding-algorithm-name: tableDateShardingAlgorithm
 * @date 2023-08-15 下午4:40
 **/
@Component(value = "databaseDateShardingAlgorithm")
public class DatabaseDateShardingAlgorithm implements StandardShardingAlgorithm {


    private static final Logger logger = LoggerFactory.getLogger(DatabaseDateShardingAlgorithm.class);


    /**
     * 精确分片
     * 根据自己想要的分片策略来修改程序
     *
     * @param availableTargetNames 有效的数据源或表的名字。这里就对应配置文件中配置的数据源信息
     * @param preciseShardingValue 包含 逻辑表名、分片列和分片列的值。
     * @return 返回数据库名
     */
    @Override
    public String doSharding(Collection availableTargetNames, PreciseShardingValue preciseShardingValue) {
        Date date = DateUtil.parseDate(preciseShardingValue.getValue().toString());
        logger.info("Sharding input:" + preciseShardingValue.getValue());
        String suffix = getSuffixByYear(date);
        //分库的规则: 时间按年取值，数据库名称是以年来结尾。比如: log2022, log2023。
        for (Object targetName : availableTargetNames) {
            if (targetName.toString().endsWith(suffix)) {
                return targetName.toString();
            }
        }
        throw new IllegalArgumentException("未找到匹配的数据库");
    }

    /**
     * 范围分片
     *
     * @param collection         有效的数据源或表的名字。这里就对应配置文件中配置的数据源信息
     * @param rangeShardingValue 包含逻辑表名、分片列和分片列的条件范围。
     * @return 返回目标结果。可以是多个。
     */
    @Override
    public Collection<String> doSharding(Collection collection, RangeShardingValue rangeShardingValue) {
        // 这里可以定义范围分片策略
        return collection;
    }


    @Override
    public void init(Properties properties) {
        //        初始化一些数据信息
        //      获取配置文件信息
    }

    private static String getSuffixByYearMonth(Date date) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(2);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR) + "" + nf.format((calendar.get(Calendar.MONTH) + 1));
    }

    private static String getSuffixByYear(Date date) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(2);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR) + "";
    }
}
