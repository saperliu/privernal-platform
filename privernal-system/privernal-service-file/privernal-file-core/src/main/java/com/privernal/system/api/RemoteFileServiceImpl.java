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

package com.privernal.system.api;

import com.privernal.common.core.utils.StringUtils;
import cn.hutool.core.io.FileUtil;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.config.OssConfig;
import com.privernal.system.constant.UploadConstants;
import com.privernal.system.domain.SystemFileInfo;
import com.privernal.system.dto.SystemFileDTO;
import com.privernal.system.feign.RemoteFileService;
import com.privernal.system.service.ISystemFileInfoService;
import com.privernal.system.service.ISystemLocalFileService;
import com.privernal.system.service.ISystemOssFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class RemoteFileServiceImpl implements RemoteFileService {
    @Autowired
    private ISystemLocalFileService sysFileService;

    @Autowired
    private ISystemOssFileService iSystemOssFileService;

    @Autowired
    private ISystemFileInfoService systemFileInfoService;


    /**
     * 资源上传使用的类型
     */
    @Value("${privernal.upload.type}")
    public String uploadType;

    /**
     * 域名或本机访问地址
     */
    @Value("${file.domain}")
    public String domain;


    @Autowired
    private OssConfig ossConfig;

    @Override
    public CommonResult<SystemFileDTO> upload(MultipartFile file, String module, String filePath, String orderId, String orderStage, String type, String request) {
        return uploadService(file, filePath, uploadType, module, orderId, orderStage, type);
    }
    /**
     * 上传文件
     *
     * @param file     文件
     * @param filePath 文件指定的目录
     * @return
     */
    private CommonResult<SystemFileDTO> uploadService(MultipartFile file, String filePath, String uploadType, String module, String sourceId, String sourceStage, String type) {
        try {
            long fileSize = file.getSize();
            String originalFileName = file.getOriginalFilename();
            if (UploadConstants.OSS.equals(uploadType) || UploadConstants.MINIO.equals(uploadType)) {
                if (StringUtils.isEmpty(filePath)) {
                    // 没有指定位置，则是 组织ID/模块 目录下面
                    filePath = SecurityUtils.getOrganizationId() + "/" + module;
                }
                SystemFileDTO sysFile = iSystemOssFileService.uploadFile(file, filePath);
                if (StringUtils.isEmpty(sysFile.getUrl())) {
                    return CommonResult.fail("上传文件失败");
                }
                String id = saveFileInfo(sysFile, fileSize,originalFileName, uploadType, module, sourceId, sourceStage, type);
                sysFile.setUrl(ossConfig.getNetPath() + "/" + sysFile.getUrl());
                sysFile.setId(id);
                return CommonResult.ok(sysFile);
            } else {
                SystemFileDTO sysFile = new SystemFileDTO();
                sysFile.setOriginalName(originalFileName);
                // 上传并返回访问地址
                String url = sysFileService.uploadFile(file);
                sysFile.setName(FileUtil.getName(url));
                sysFile.setUrl(domain + url);
                String id = saveFileInfo(sysFile, fileSize, originalFileName, uploadType, module, sourceId, sourceStage, type);
                sysFile.setId(id);
                return CommonResult.ok(sysFile);
            }
        } catch (Exception e) {
            log.error("上传文件失败", e);
            return CommonResult.fail(e.getMessage());
        }
    }

    /**
     * 保存文件信息
     *
     * @param sysFile     文件位置信息
     * @param fileSize        文件大小
     * @param originalFileName        文件名
     * @param module      模块
     * @param uploadType  上传类型  OSS LOCAL MINIO
     * @param sourceId    数据ID
     * @param sourceStage 数据的阶段，提交/审批/验收，备用，默认为INIT
     * @param type        类型，是文件还是图片
     */
    private String saveFileInfo(SystemFileDTO sysFile, long fileSize,String originalFileName, String uploadType, String module, String sourceId, String sourceStage, String type) {
        SystemFileInfo systemFileInfo = new SystemFileInfo();
        systemFileInfo.setSourceId(sourceId);
        if (StringUtils.isEmpty(type)) {
            systemFileInfo.setSourceStage(UploadConstants.ORDER_STAGE_INIT);
        } else {
            systemFileInfo.setSourceStage(sourceStage);
        }

        if (StringUtils.isEmpty(type)) {
            systemFileInfo.setType(UploadConstants.FILE_TYPE_FILE);
        } else {
            systemFileInfo.setType(type);
        }
        systemFileInfo.setModule(module);
        systemFileInfo.setFileSize(fileSize);
        systemFileInfo.setFileStoreType(uploadType);
        systemFileInfo.setDepartmentId(SecurityUtils.getDepartmentId());
        systemFileInfo.setImagePath(sysFile.getPath());
        systemFileInfo.setImageUrl(sysFile.getUrl());
        systemFileInfo.setNewName(FileUtil.getName(sysFile.getUrl()));
        systemFileInfo.setOriginalName(originalFileName);
        systemFileInfo.setOrganizationId(SecurityUtils.getOrganizationId());
        systemFileInfoService.insertSystemFileInfo(systemFileInfo);
        return systemFileInfo.getId();
    }

}
