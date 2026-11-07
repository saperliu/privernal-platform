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

package com.privernal.common.rocketmq.calculate;

import java.util.ArrayList;
import java.util.List;

/**
 * @author saperliu
 * @version 1.0
 * @date 2022/1/7 3:49
 * @return 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
 */
public class DelayLevelCalculate {

    private static List<Integer> defaultLevel;

    static {
        defaultLevel = new ArrayList<>();
        defaultLevel.add(1);
        defaultLevel.add(5);
        defaultLevel.add(10);
        defaultLevel.add(30);
        defaultLevel.add(60);
        defaultLevel.add(120);
        defaultLevel.add(180);
        defaultLevel.add(240);
        defaultLevel.add(300);
        defaultLevel.add(360);
        defaultLevel.add(420);
        defaultLevel.add(480);
        defaultLevel.add(540);
        defaultLevel.add(600);
        defaultLevel.add(1200);
        defaultLevel.add(1800);
        defaultLevel.add(3600);
        defaultLevel.add(7200);
    }

    public static Integer calculateDefault(long second) {
        Integer level = null;
        for (int i = defaultLevel.size() - 1; i >= 0; i--) {
            int l = (int) second / defaultLevel.get(i);
            if (l > 0 && level == null) {
                level = i;
            }
            if (level == null) {
                continue;
            }
            if (level < i) {
                break;
            }
        }
        if(level == null){
            return 1;
        }
        return level + 1;
    }

    public static int calculateNum(long second) {
        for (int i = defaultLevel.size() - 1; i >= 0; i--) {
            int l = (int) second / defaultLevel.get(i);
            if (l != 0) {
                return l;
            }
        }
        return 0;
    }

    public static int get(Integer index) {
        return defaultLevel.get(index);
    }


}
