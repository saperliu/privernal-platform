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
 * 应用包 dry-run 预检报告（R2-14）：解析包内容并与目标环境比对后的「将发生什么」清单，
 * 全程只读、不落库。每项的 predicted 与实际导入动作一一对应，导入方看完报告再决定是否执行。
 *
 * @author saperliu
 */
@Data
public class AppPackageDryRunResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标环境已有同编码应用，导入将直接复用（不新建） */
    public static final String REUSE = "reuse";
    /** 目标环境不存在，导入将新建 */
    public static final String CREATE = "create";
    /** 目标环境已存在且开启了 overwrite，导入将覆盖更新 */
    public static final String UPDATE = "update";
    /** 目标环境已存在但未开启 overwrite，导入将跳过 */
    public static final String SKIP = "skip";
    /** 数据安全隐患（如覆盖会删除目标环境字段），导入将拒绝该项 */
    public static final String CONFLICT = "conflict";

    private String appCode;

    private String appName;

    private String specVersion;

    /** 包签名状态：verified / mismatch / notSigned / keyMissing（PackageSignatures.Status 转小驼峰） */
    private String signatureStatus;

    private String signatureDetail;

    /** 包体校验和是否与重算值一致（无 checksum 字段的旧包为 false 且仅提示） */
    private boolean checksumMatch;

    /** 本环境是否配置了 openapi.secret-master-key（决定已签包能否验签） */
    private boolean signatureKeyConfigured;

    /** 本次 dry-run 使用的 overwrite 参数 */
    private boolean overwrite;

    private List<Item> items = new ArrayList<>();

    /** 不阻断导入但导入方应知情的observations：缺表单引用、角色不存在、未签名等 */
    private List<String> warnings = new ArrayList<>();

    public void add(String type, String code, String name, String predicted, String message) {
        Item item = new Item();
        item.setType(type);
        item.setCode(code);
        item.setName(name);
        item.setPredicted(predicted);
        item.setMessage(message);
        items.add(item);
    }

    public long count(String predicted) {
        return items.stream().filter(item -> predicted.equals(item.getPredicted())).count();
    }

    public boolean hasConflict() {
        return items.stream().anyMatch(item -> CONFLICT.equals(item.getPredicted()));
    }

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;
        /** app/form/flow/flowForm/dataset/report/dashboard/datavScreen/datavAsset/schedule/docTemplate/dict/roleBinding */
        private String type;
        private String code;
        private String name;
        private String predicted;
        private String message;
    }
}
