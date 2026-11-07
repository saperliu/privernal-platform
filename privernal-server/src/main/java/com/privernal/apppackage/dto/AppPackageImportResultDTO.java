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

package com.privernal.apppackage.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用包导入结果：逐项给出实际执行的动作，供导入方判断目标环境是否被改动。
 *
 * @author saperliu
 */
@Data
public class AppPackageImportResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String SKIPPED = "skipped";
    public static final String FAILED = "failed";

    private String appCode;

    private String appAction;

    private List<Item> items = new ArrayList<>();

    public void add(String type, String code, String action, String message) {
        Item item = new Item();
        item.setType(type);
        item.setCode(code);
        item.setAction(action);
        item.setMessage(message);
        items.add(item);
    }

    public long count(String action) {
        return items.stream().filter(item -> action.equals(item.getAction())).count();
    }

    public boolean hasFailure() {
        return items.stream().anyMatch(item -> FAILED.equals(item.getAction()));
    }

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;
        /** form / flow / flowForm */
        private String type;
        private String code;
        private String action;
        private String message;
    }
}
