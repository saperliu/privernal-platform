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

package com.privernal.common.core.enums;

/**
 * 系统模块,
 * 系统内的各个应用编码
 *
 * @author saperliu
 */
public enum ProductModuleEnums {
    GLOBAL("GLOBAL", "系统"),
    OPERATION("OPERATION", "运行管理"),
    LEDGER("LEDGER", "台账管理"),
    STANDARD("STANDARD", "标准管理"),
    REPAIR("REPAIR", "报事报修"),
    WAREHOUSE("WAREHOUSE", "物资管理"),
    QUALITY("QUALITY", "品质稽查"),
    ENERGY("ENERGY", "能耗统计"),
    CONSTRUCTION("CONSTRUCTION", "施工管理"),
    INTEGRATED("INTEGRATED", "集成监测"),
    ASSETS("ASSETS", "固定资产"),
    SATISFACTION("SATISFACTION", "问卷调查"),
    EVALUATION("EVALUATION", "项目等级评定"),
//    TRAINING("TRAINING", "培训管理"),
    LOCATION("LOCATION", "空间运营"),
    UNDERTAKE("UNDERTAKE", "承接查验"),
    CERTIFICATE("CERTIFICATE", "证书管理"),
    EMERGENCY("EMERGENCY", "应急演练"),
//    COMMERCIAL("COMMERCIAL", "社商资源"),
    VISITOR("VISITOR", "访客管理"),
    PAYMENT("PAYMENT", "在线缴费"),
    RISK("RISK", "项目风险管理"),

    SYSTEM("SYSTEM", "系统管理"),
    PLATFORM("PLATFORM", "平台管理");

    private final String code;
    private final String info;

    ProductModuleEnums(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

    public static String geInfoByCode(String code) {
        for (ProductModuleEnums value : values()) {
            if (value.getCode().equals(code)) {
                return value.info;
            }
        }
        return "";
    }

}
