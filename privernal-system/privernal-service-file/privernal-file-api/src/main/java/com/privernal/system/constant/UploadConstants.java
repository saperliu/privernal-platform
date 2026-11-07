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

package com.privernal.system.constant;

/**
 * 上传类型的标识
 *
 * @author saperliu
 */
public class UploadConstants {
    /**
     * OSS上传类型
     */
    public static final String OSS = "OSS";

    /**
     * LOCAL 上传类型
     */
    public static final String LOCAL = "LOCAL";

    /**
     * MINIO 上传类型
     */
    public static final String MINIO = "MINIO";


    /**
     * 文件类型为图片
     */
    public static final String FILE_TYPE_IMAGE = "IMAGE";
    /**
     * 文件类型为附件
     */
    public static final String FILE_TYPE_FILE = "FILE";

    /**
     * 头像存储的目录
     */
    public static final String AVATAR_STORE_PATH = "avatar";

    /**
     * 二维码存储的目录
     */
    public static final String QCODE_STORE_PATH = "qcode";

    /**
     * 订单的默认阶段
     */
    public static final String ORDER_STAGE_INIT= "INIT";



}
