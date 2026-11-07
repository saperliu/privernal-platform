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

package com.privernal.common.core.utils.file;

import java.util.Map;

/**
 * 媒体类型工具类
 *
 * @author saperliu
 */
public class MimeTypeUtils
{
    public static final String IMAGE_PNG = "image/png";

    public static final String IMAGE_JPG = "image/jpg";

    public static final String IMAGE_JPEG = "image/jpeg";

    public static final String IMAGE_BMP = "image/bmp";

    public static final String IMAGE_GIF = "image/gif";

    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
            // 图片
            "bmp", "gif", "jpg", "jpeg", "png",
            // word excel powerpoint（html/htm 不进白名单：上传域与静态资源同源，
            // 内联渲染 text/html 等于开放存储型 XSS；需要网页内容请走打印/导出链路）
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
            // 压缩文件
            "rar", "zip", "gz", "bz2",
            // 视频格式
            "mp4", "avi", "rmvb",
            // pdf
            "pdf" };

    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            IMAGE_PNG, "png",
            IMAGE_JPG, "jpg",
            IMAGE_JPEG, "jpeg",
            IMAGE_BMP, "bmp",
            IMAGE_GIF, "gif");

    /**
     * 按 MIME 反查图片扩展名，未知类型返回空串。
     */
    public static String getExtension(String mime)
    {
        return EXTENSION_BY_MIME.getOrDefault(mime, "");
    }
}
