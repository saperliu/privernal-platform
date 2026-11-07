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

package com.privernal.system.service;

import com.privernal.system.dto.SystemFileDTO;
import com.aliyun.oss.model.OSSObject;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传接口
 *
 * @author saperliu
 * @date 2021-12-19
 */
public interface ISystemOssFileService {
    /**
     * 文件上传接口
     *
     * @param file     上传的文件
     * @param filePath 上传的文件存放路径
     * @return 访问地址
     * @throws Exception
     */
    public SystemFileDTO uploadFile(MultipartFile file, String filePath) throws Exception;

    /**
     * 获取文件流
     *
     * @param fileName 文件名
     * @return
     */
    public OSSObject downLoadImage(String fileName);

    /**
     * 删除文件
     *
     * @param fileName 文件名
     * @return
     */
    public SystemFileDTO deleteImage(List<String> fileName);
}
