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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传白名单与 MIME 反查的行为基线。
 *
 * 白名单直接决定「用户能往服务器传哪些文件类型」，
 * 因此这里锁定的不是格式，而是安全边界：可增，不可减。
 */
class MimeTypeUtilsTest
{
    @Test
    @DisplayName("五种图片 MIME 能反查到扩展名")
    void knownImageMimesResolve()
    {
        assertEquals("png", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_PNG));
        assertEquals("jpg", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_JPG));
        assertEquals("jpeg", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_JPEG));
        assertEquals("bmp", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_BMP));
        assertEquals("gif", MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_GIF));
    }

    @Test
    @DisplayName("非图片类型与未知 MIME 一律返回空串，不抛异常")
    void unknownMimeReturnsEmpty()
    {
        assertEquals("", MimeTypeUtils.getExtension("application/pdf"));
        assertEquals("", MimeTypeUtils.getExtension("text/plain"));
        assertEquals("", MimeTypeUtils.getExtension(""));
    }

    @Test
    @DisplayName("默认白名单含文档/图片/压缩包/pdf，且不含可执行与脚本后缀")
    void defaultAllowedExtensionsCoverCommonUploads()
    {
        List<String> allowed = Arrays.asList(MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        assertTrue(allowed.containsAll(Arrays.asList(
                "bmp", "gif", "jpg", "jpeg", "png",
                "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "rar", "zip", "gz", "bz2", "mp4", "avi", "rmvb", "pdf")));
        for (String dangerous : new String[] { "exe", "sh", "bat", "jsp", "jspx", "html", "htm" })
        {
            // html/htm 已随 S13 整改移出白名单（上传域与 /statics 同源，内联渲染即存储型 XSS）
            assertFalse(allowed.contains(dangerous), "白名单不应包含 " + dangerous);
        }
    }
}
