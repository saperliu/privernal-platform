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

package com.privernal.common.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "privernal.web")
@Validated
@Data
public class WebProperties {

    @NotNull(message = "APP API 不能为空")
    private Api appApi = new Api("/app", "**.controller.app.**");

    @NotNull(message = "PC端 API 不能为空")
    private Api adminApi = new Api("/back", "**.controller.pc.**");

    @NotNull(message = "Admin UI 不能为空")
    private Ui adminUi;

    public Api getAppApi() {
        return appApi;
    }

    public void setAppApi(Api appApi) {
        this.appApi = appApi;
    }

    public Api getAdminApi() {
        return adminApi;
    }

    public void setAdminApi(Api adminApi) {
        this.adminApi = adminApi;
    }

    public Ui getAdminUi() {
        return adminUi;
    }

    public void setAdminUi(Ui adminUi) {
        this.adminUi = adminUi;
    }

    @Data
    @Valid
    public static class Api {

        @NotEmpty(message = "API 前缀不能为空")
        private String prefix;

        @NotEmpty(message = "Controller 所在包不能为空")
        private String controller;

        public Api() {
        }

        public Api(String prefix, String controller) {
            this.prefix = prefix;
            this.controller = controller;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getController() {
            return controller;
        }

        public void setController(String controller) {
            this.controller = controller;
        }

    }

    @Data
    @Valid
    public static class Ui {

        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

    }

}