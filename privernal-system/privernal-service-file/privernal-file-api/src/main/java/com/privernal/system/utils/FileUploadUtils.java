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

import cn.hutool.core.util.IdUtil;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.DateUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.file.MimeTypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Set;

/**
 * 本地磁盘上传：校验 → 按日期分目录重新命名 → 落盘。
 *
 * <p>落盘文件名一律由日期目录与生成的 ID 组成，客户端提供的原始名只用于取扩展名和长度校验，
 * 因此原始名里的路径分隔符不会参与拼接。</p>
 *
 * @author saperliu
 */
@Slf4j
public class FileUploadUtils {

    /** 单个文件大小上限 */
    private static final long MAX_SIZE = 50L * 1024 * 1024;

    /** 原始文件名长度上限；落盘名是生成的 ID，不受此约束 */
    private static final int MAX_ORIGINAL_NAME_LENGTH = 100;

    /** 落盘权限 rw-r--r-- */
    private static final Set<PosixFilePermission> PERMISSION_READ_WRITE =
            PosixFilePermissions.fromString("rw-r--r--");

    private FileUploadUtils() {
    }

    /**
     * 按默认白名单上传
     *
     * @param baseDir 应用相对基目录
     * @param file    上传的文件
     * @return 供前端拼接访问地址的相对路径
     */
    public static String upload(String baseDir, MultipartFile file) throws IOException {
        return upload(baseDir, file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
    }

    /**
     * 上传文件
     *
     * @param baseDir          应用相对基目录
     * @param file             上传的文件
     * @param allowedExtension 允许的扩展名；传 null 表示不做扩展名校验
     * @return 供前端拼接访问地址的相对路径
     */
    public static String upload(String baseDir, MultipartFile file, String[] allowedExtension) throws IOException {
        assertAllowed(file, allowedExtension);

        String storedName = extractFilename(file);
        Path target = Paths.get(baseDir, storedName).normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // 用流拷贝代替 transferTo：undertow 的小文件上传不落临时盘，
        // transferTo 依赖容器临时文件会抛 NoSuchFileException
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        applyReadWritePermission(target);
        return "/" + storedName;
    }

    /**
     * 上传前置校验（供 OSS/MinIO 等不落盘的上传链复用）：文件名长度、大小与扩展名白名单。
     * 对象存储上传不经过 {@link #upload}，也必须过同一份校验，否则白名单形同虚设。
     */
    public static void assertAllowed(MultipartFile file, String[] allowedExtension) {
        String originalName = requireOriginalName(file);
        if (originalName.length() > MAX_ORIGINAL_NAME_LENGTH) {
            throw new ServiceException(StringUtils.format("文件名长度超过限制：{}，当前 {} 个字符",
                    MAX_ORIGINAL_NAME_LENGTH, originalName.length()));
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ServiceException(StringUtils.format("文件大小超过限制：{} MB", MAX_SIZE / 1024 / 1024));
        }
        String extension = getExtension(file);
        if (allowedExtension != null && !isAllowedExtension(extension, allowedExtension)) {
            throw new ServiceException(StringUtils.format("文件 [{}] 的扩展名 [{}] 不在允许列表 {} 内",
                    originalName, extension, Arrays.toString(allowedExtension)));
        }
    }

    /**
     * 生成落盘文件名：{@code 日期目录/ID.扩展名}
     */
    public static String extractFilename(MultipartFile file) {
        return DateUtils.datePath() + "/" + IdUtil.getSnowflakeNextIdStr() + "." + getExtension(file);
    }

    /**
     * 取扩展名：优先用原始文件名后缀，没有则按 MIME 类型反查。
     * 一律归一为小写——静态服务与下载侧常按扩展名匹配，{@code .PNG} 与 {@code .png} 不等价。
     */
    public static String getExtension(MultipartFile file) {
        String extension = FilenameUtils.getExtension(requireOriginalName(file));
        if (StringUtils.isEmpty(extension)) {
            extension = MimeTypeUtils.getExtension(file.getContentType());
        }
        return extension.toLowerCase();
    }

    private static boolean isAllowedExtension(String extension, String[] allowedExtension) {
        for (String allowed : allowedExtension) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String requireOriginalName(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalName)) {
            throw new ServiceException("上传请求缺少文件名");
        }
        return originalName;
    }

    /**
     * Windows 等非 POSIX 文件系统不支持该操作，跳过即可，不能让已经写成功的文件反过来报错
     */
    private static void applyReadWritePermission(Path target) {
        try {
            Files.setPosixFilePermissions(target, PERMISSION_READ_WRITE);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("设置文件权限失败，已忽略：{}", target);
        }
    }
}
