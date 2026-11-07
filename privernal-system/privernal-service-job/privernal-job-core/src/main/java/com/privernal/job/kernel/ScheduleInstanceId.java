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

package com.privernal.job.kernel;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 调度实例标识：ip + pid + 启动时间（§6.4 instance_id 口径）。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class ScheduleInstanceId {

    @Getter
    private String instanceId;

    @Getter
    private Date startTime = new Date();

    @PostConstruct
    public void init() {
        String ip = "127.0.0.1";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("resolve local ip failed, fallback 127.0.0.1", e);
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        this.instanceId = ip + ":" + pid + ":" + new SimpleDateFormat("yyyyMMddHHmmss").format(startTime);
        log.info("[schedule] instance boot: {}", instanceId);
    }
}
