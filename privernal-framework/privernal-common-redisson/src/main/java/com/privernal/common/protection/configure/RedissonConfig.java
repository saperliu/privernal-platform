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

package com.privernal.common.protection.configure;

import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-01-15 18:37
 * @Description
 */
@Configuration
@ConditionalOnClass(Config.class)
public class RedissonConfig {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*单节点配置*/
    @Value("${spring.data.redis.host:}")
    private String host;
    @Value("${spring.data.redis.port:}")
    private String port;
    /*哨兵模式配置*/
    @Value("${spring.data.redis.sentinel.nodes:}")
    private String sentinel;
    @Value("${spring.data.redis.sentinel.master:}")
    private String master;
    /*集群模式配置*/
    @Value("${spring.data.redis.nodes:}")
    private String nodes;

    @Value("${spring.data.redis.password:}")
    private String password;
    @Value("${spring.data.redis.timeout:1000}")
    private int timeout;
    @Value("${spring.redisson.connectionPoolSize:64}")
    private int connectionPoolSize;
    @Value("${spring.redisson.connectionMinimumIdleSize:10}")
    private int connectionMinimumIdleSize;
    @Value("${spring.redisson.slaveConnectionPoolSize:250}")
    private int slaveConnectionPoolSize;
    @Value("${spring.redisson.masterConnectionPoolSize:250}")
    private int masterConnectionPoolSize;


    @Bean
    public RedissonClient redissonClient(){
        if (StringUtils.isNotBlank(sentinel) && StringUtils.isNotBlank(master)) {
            logger.info("->->->->Redisson use SentinelServersConfig ");
            String[] nodesList = sentinel.split(",");
            for(int i=0;i<nodesList.length;i++) {
                nodesList[i] = "redis://" + nodesList[i];
            }

            Config config = new Config();
            SentinelServersConfig serverConfig = config.useSentinelServers()
                    .addSentinelAddress(nodesList)
                    .setMasterName(master)
                    .setTimeout(timeout)
                    .setMasterConnectionPoolSize(masterConnectionPoolSize)
                    .setSlaveConnectionPoolSize(slaveConnectionPoolSize);
            if (!StringUtils.isEmpty(password)) {
                serverConfig.setPassword(password);
            }
            return Redisson.create(config);
        }else if(StringUtils.isNotBlank(host)){
            logger.info("->->->->Redisson use SingleServerConfig ");
            Config config = new Config();
            SingleServerConfig singleServerConfig = config.useSingleServer()
                    .setAddress("redis://"+host+":"+port)
                    .setTimeout(timeout);
            if (!StringUtils.isEmpty(password)) {
                singleServerConfig.setPassword(password);
            }
            return Redisson.create(config);
        }else{
            logger.info("->->->->Redisson use ClusterServersConfig ");
            String[] nodesList = nodes.split(",");
            for(int i=0;i<nodesList.length;i++) {
                nodesList[i] = "redis://" + nodesList[i];
            }

            Config config = new Config();
            ClusterServersConfig serverConfig = config.useClusterServers()
                    .addNodeAddress(nodesList)
                    .setTimeout(timeout)
                    .setMasterConnectionPoolSize(masterConnectionPoolSize)
                    .setSlaveConnectionPoolSize(slaveConnectionPoolSize);
            if (!StringUtils.isEmpty(password)) {
                serverConfig.setPassword(password);
            }
            return Redisson.create(config);
        }
    }
}

