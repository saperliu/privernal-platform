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

package com.privernal.common.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ID 生成的行为基线。
 *
 * <p>在用的只有 simpleUUID：验证码等场景用它做键名，
 * 所以锁住「32 位小写十六进制、无横线、不重复」这三条对外可见的约定。</p>
 */
class IdUtilsTest
{
    @Test
    @DisplayName("simpleUUID 返回 32 位小写十六进制且不含横线")
    void simpleUuidShape()
    {
        String id = IdUtils.simpleUUID();
        assertNotNull(id);
        assertEquals(32, id.length(), "应为 32 个字符，实际：" + id);
        assertFalse(id.contains("-"), "不应含横线：" + id);
        assertTrue(id.matches("^[0-9a-f]{32}$"), "应为小写十六进制，实际：" + id);
    }

    @Test
    @DisplayName("连续调用不重复（键名靠它做唯一）")
    void simpleUuidIsUnique()
    {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < 5000; i++)
        {
            assertTrue(ids.add(IdUtils.simpleUUID()), "第 " + i + " 次生成出现重复 ID");
        }
    }

    @Test
    @DisplayName("能在并发下保持唯一（验证码生成走多线程）")
    void simpleUuidIsUniqueAcrossThreads() throws InterruptedException
    {
        int threads = 8;
        int perThread = 1000;
        java.util.Set<String> shared = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.List<Thread> workers = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++)
        {
            Thread w = new Thread(() -> {
                for (int i = 0; i < perThread; i++)
                {
                    shared.add(IdUtils.simpleUUID());
                }
            });
            workers.add(w);
            w.start();
        }
        for (Thread w : workers)
        {
            w.join();
        }
        assertEquals(threads * perThread, shared.size(), "并发生成出现碰撞");
    }
}
