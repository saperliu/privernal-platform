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

package com.privernal.system.utils;

import com.privernal.common.core.exception.ServiceException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地上传的校验与落盘行为。
 *
 * <p>重点锁两件事：一是坏输入必须以业务异常拒绝而不是写坏文件，
 * 二是客户端给的原始名不得参与路径拼接——上传目录一旦被 {@code ..} 穿透就是任意文件写入。</p>
 */
class FileUploadUtilsTest
{
    @TempDir
    Path base;

    private static MockMultipartFile file(String name, String mime, byte[] content)
    {
        return new MockMultipartFile("file", name, mime, content);
    }

    @Test
    @DisplayName("合法上传：落盘、内容一致、返回可拼接的相对路径")
    void uploadsAllowedExtension() throws IOException
    {
        byte[] content = "fake-png-bytes".getBytes();
        String relative = FileUploadUtils.upload(base.toString(), file("photo.png", "image/png", content));

        assertTrue(relative.matches("^/\\d{4}/\\d{2}/\\d{2}/\\d+\\.png$"), "实际返回：" + relative);
        Path stored = base.resolve(relative.substring(1));
        assertTrue(Files.exists(stored), "文件应真实落盘：" + stored);
        assertArrayEquals(content, Files.readAllBytes(stored));
    }

    @Test
    @DisplayName("落盘权限为 rw-r--r--，不支持 POSIX 的平台跳过而不报错")
    void appliesReadWritePermission() throws IOException
    {
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        Assumptions.assumeTrue(posix, "当前平台不支持 POSIX 权限，跳过断言");

        String relative = FileUploadUtils.upload(base.toString(),
                file("photo.png", "image/png", new byte[] { 1 }));
        String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(base.resolve(relative.substring(1))));
        assertEquals("rw-r--r--", perms);
    }

    @Test
    @DisplayName("白名单外的扩展名一律拒绝，且不留下文件")
    void rejectsIllegalExtension() throws IOException
    {
        ServiceException e = assertThrowsService(() -> FileUploadUtils.upload(base.toString(),
                file("shell.jsp", "application/octet-stream", "x".getBytes())));
        assertTrue(e.getMessage().contains("扩展名"), e.getMessage());
        try (var walk = Files.walk(base))
        {
            assertFalse(walk.anyMatch(p -> p.getFileName().toString().endsWith(".jsp")), "拒绝后不应留下文件");
        }
    }

    @Test
    @DisplayName("扩展名大小写不影响白名单判断")
    void extensionMatchIsCaseInsensitive() throws IOException
    {
        String relative = FileUploadUtils.upload(base.toString(),
                file("PHOTO.PNG", "image/png", new byte[] { 1 }));
        assertTrue(relative.endsWith(".png"), "落盘名用小写扩展名：" + relative);
    }

    @Test
    @DisplayName("超过 50M 拒绝")
    void rejectsOversizedFile()
    {
        MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", new byte[] { 1 })
        {
            @Override
            public long getSize()
            {
                return 51L * 1024 * 1024;
            }
        };
        ServiceException e = assertThrowsService(() -> FileUploadUtils.upload(base.toString(), big));
        assertTrue(e.getMessage().contains("大小"), e.getMessage());
    }

    @Test
    @DisplayName("原始文件名超长拒绝")
    void rejectsOverlongOriginalName()
    {
        String longName = "x".repeat(97) + ".png";
        assertEquals(101, longName.length());
        ServiceException e = assertThrowsService(() -> FileUploadUtils.upload(base.toString(),
                file(longName, "image/png", new byte[] { 1 })));
        assertTrue(e.getMessage().contains("长度"), e.getMessage());
    }

    @Test
    @DisplayName("缺少文件名时不得抛 NPE")
    void rejectsMissingName()
    {
        MockMultipartFile noName = new MockMultipartFile("file", new byte[] { 1 });
        ServiceException e = assertThrowsService(() -> FileUploadUtils.upload(base.toString(), noName));
        assertTrue(e.getMessage().contains("文件名"), e.getMessage());
    }

    @Test
    @DisplayName("原始名里的路径分隔符不参与拼接，上传目录不会被穿透")
    void originalNameCannotEscapeBaseDir() throws IOException
    {
        String relative = FileUploadUtils.upload(base.toString(),
                file("../../../../tmp/evil.png", "image/png", "x".getBytes()));
        Path stored = base.resolve(relative.substring(1)).normalize();
        assertTrue(stored.startsWith(base.toAbsolutePath()) || stored.startsWith(base),
                "落盘位置必须仍在基目录内：" + stored);
        assertFalse(relative.contains(".."), "返回路径不应含 ..：" + relative);
    }

    @Test
    @DisplayName("扩展名优先取文件名后缀，没有则按 MIME 反查")
    void extensionFallsBackToContentType()
    {
        assertEquals("png", FileUploadUtils.getExtension(file("a.png", "application/octet-stream", new byte[] { 1 })));
        assertEquals("png", FileUploadUtils.getExtension(file("noext", "image/png", new byte[] { 1 })));
        assertEquals("jpeg", FileUploadUtils.getExtension(file("noext", "image/jpeg", new byte[] { 1 })));
        assertEquals("", FileUploadUtils.getExtension(file("noext", "application/octet-stream", new byte[] { 1 })));
    }

    @Test
    @DisplayName("同一毫秒内的两个文件名也不相同")
    void generatedNamesDoNotCollide()
    {
        String a = FileUploadUtils.extractFilename(file("x.png", "image/png", new byte[] { 1 }));
        String b = FileUploadUtils.extractFilename(file("x.png", "image/png", new byte[] { 1 }));
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("显式传白名单时按传入的列表判断；传 null 表示不校验")
    void honoursExplicitAllowList() throws IOException
    {
        ServiceException e = assertThrowsService(() -> FileUploadUtils.upload(base.toString(),
                file("a.txt", "text/plain", new byte[] { 1 }), new String[] { "png" }));
        assertTrue(Arrays.toString(new String[] { "png" }).contains("png"));
        assertTrue(e.getMessage().contains("允许列表"), e.getMessage());

        String relative = FileUploadUtils.upload(base.toString(),
                file("a.txt", "text/plain", new byte[] { 1 }), null);
        assertTrue(relative.endsWith(".txt"), "null 白名单不校验扩展名：" + relative);
    }

    private static ServiceException assertThrowsService(ThrowingRunnable action)
    {
        try
        {
            action.run();
        }
        catch (ServiceException e)
        {
            return e;
        }
        catch (Exception e)
        {
            throw new AssertionError("期望 ServiceException，实际是 " + e.getClass().getName() + ": " + e.getMessage(), e);
        }
        throw new AssertionError("期望抛出 ServiceException，但没有抛出");
    }

    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }
}
