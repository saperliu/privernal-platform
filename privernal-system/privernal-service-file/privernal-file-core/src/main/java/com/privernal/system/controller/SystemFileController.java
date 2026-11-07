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

package com.privernal.system.controller;

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.core.utils.StringUtils;
import cn.hutool.core.io.FileUtil;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.config.OssConfig;
import com.privernal.system.constant.UploadConstants;
import com.privernal.system.domain.SystemFileInfo;
import com.privernal.system.dto.SystemFileDTO;
import com.privernal.system.service.ISystemFileInfoService;
import com.privernal.system.service.ISystemLocalFileService;
import com.privernal.system.service.ISystemOssFileService;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.aliyun.oss.model.OSSObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件请求处理
 *
 * @author saperliu
 */
@Tag(name = "文件上传相关接口")
@RestController
public class SystemFileController {
    private static final Logger log = LoggerFactory.getLogger(SystemFileController.class);

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

    /**
     * 文件上传请求
     */
    @Operation(summary = "文件上传,内部接口")
    @ServiceRequest
    @PostMapping("/inner/file/upload")
    public CommonResult<SystemFileDTO> innerUpload(@RequestParam("file") MultipartFile file, @RequestParam("module") String module, @RequestParam(value = "filePath", required = false) String filePath, @RequestParam("sourceId") String sourceId, @RequestParam(value = "sourceStage", required = false) String sourceStage, @RequestParam(value = "type", required = false) String type) {
        if (StringUtils.isEmpty(filePath)) {
            filePath = ossConfig.getPath();
        }
        return upload(file, filePath, uploadType, module, sourceId, sourceStage, type);
    }

    /**
     * 文件上传请求
     */
    @Operation(summary = "二维码上传,并识别返回内容")
    @Log(title = "文件上传", operationType = OperationType.INSERT)
    @PostMapping("/back/file/qrcode/upload")
    public CommonResult<SystemFileDTO> qrCodeUpload(@RequestParam("file") MultipartFile file, @RequestParam(value = "filePath", required = false) String filePath) {
        try {
            // 上传并返回访问地址
            if (StringUtils.isEmpty(filePath)) {
                filePath = UploadConstants.QCODE_STORE_PATH;
            }
            SystemFileDTO sysFile = iSystemOssFileService.uploadFile(file, filePath);
            // 是否需要识别二维码
            try {
                if (StringUtils.isEmpty(sysFile.getUrl())) {
                    return CommonResult.fail("上传文件失败");
                }
                String decode = QrCodeUtil.decode(file.getInputStream());
                sysFile.setQrCode(decode);
            } catch (Exception eCode) {
                log.error(" qrcode {}", eCode.getMessage());
            }
            return CommonResult.ok(sysFile);
        } catch (Exception e) {
            log.error("上传文件失败", e);
            return CommonResult.fail(e.getMessage());
        }
    }

    /**
     * 文件上传请求
     */
    @Operation(summary = "图片上传")
    @Log(title = "文件上传", operationType = OperationType.INSERT)
    @PostMapping("/back/file/upload")
    public CommonResult<Object> ossUpload(@RequestParam("file") MultipartFile[] file, @RequestParam("module") String module, @RequestParam("sourceId") String sourceId, @RequestParam(value = "filePath", required = false) String filePath, @RequestParam(value = "sourceStage", required = false) String sourceStage, @RequestParam(value = "type", required = false) String type) {
        List<SystemFileDTO> list = new ArrayList<>();
        for (MultipartFile file1 : file) {
            CommonResult<SystemFileDTO> result = upload(file1, filePath, uploadType, module, sourceId, sourceStage, type);
            if (result.getCode() == CommonResult.SUCCESS) {
                list.add(result.getData());
            } else {
                return CommonResult.fail(result.getMessage());
            }
        }
        return CommonResult.ok(list);
    }

    /**
     * 单文件上传（动态表单设计器的图片/附件上传组件）
     * 返回 data 为单个文件对象（含 url 字段），与组件的响应解析格式匹配
     */
    @Operation(summary = "单文件上传")
    @Log(title = "文件上传", operationType = OperationType.INSERT)
    @PostMapping("/back/file/upload/single")
    public CommonResult<SystemFileDTO> uploadSingle(@RequestParam("file") MultipartFile file, @RequestParam(value = "module", required = false, defaultValue = "dynamic-form") String module, @RequestParam(value = "sourceId", required = false, defaultValue = "0") String sourceId, @RequestParam(value = "filePath", required = false) String filePath, @RequestParam(value = "sourceStage", required = false) String sourceStage, @RequestParam(value = "type", required = false) String type) {
        return upload(file, filePath, uploadType, module, sourceId, sourceStage, type);
    }


    /**
     * 下载文件
     *
     * @param fileName 文件名，不包含域名：/images/repair/2023/07/18/ew.jpg
     * @param response 响应流输出
     */
    @Operation(summary = "下载文件")
    @GetMapping("/back/file/downLoad")
    public void downLoadImage(@RequestParam("fileName") String fileName, HttpServletResponse response) {
        if (StringUtils.isNotBlank(fileName)) {
            OSSObject ossObject = iSystemOssFileService.downLoadImage(fileName);
            if (ossObject != null) {
                InputStream inputStream = ossObject.getObjectContent();
                int buffer = 1024 * 10;
                byte[] data = new byte[buffer];
                try {
                    response.setContentType("application/octet-stream");
                    // 文件名可以任意指定
                    response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));//将文件名转为ASCLL编码
                    int read;
                    while ((read = inputStream.read(data)) != -1) {
                        response.getOutputStream().write(data, 0, read);
                    }
                    response.getOutputStream().flush();
                    response.getOutputStream().close();
                    ossObject.close();
                } catch (UnsupportedEncodingException e) {
                    log.error("  downLoadImage exception {} ", e.getMessage());
                } catch (IOException e) {
                    log.error("downLoadImage exception", e);
                }
            }
        }
    }

    /**
     * 上传文件
     *
     * @param file     文件
     * @param filePath 文件指定的目录
     * @return
     */
    private CommonResult<SystemFileDTO> upload(MultipartFile file, String filePath, String uploadType, String module, String sourceId, String sourceStage, String type) {
        try {
            if (UploadConstants.OSS.equals(uploadType) || UploadConstants.MINIO.equals(uploadType)) {
                if (StringUtils.isEmpty(filePath)) {
                    // 没有指定位置，则是 组织ID/模块 目录下面
                    filePath = SecurityUtils.getOrganizationId() + "/" + module;
                }
                SystemFileDTO sysFile = iSystemOssFileService.uploadFile(file, filePath);
                if (StringUtils.isEmpty(sysFile.getUrl())) {
                    return CommonResult.fail("上传文件失败");
                }
                String id = saveFileInfo(sysFile, file, uploadType, module, sourceId, sourceStage, type);
                sysFile.setUrl(ossConfig.getNetPath() + "/" + sysFile.getUrl());
                sysFile.setId(id);
                return CommonResult.ok(sysFile);
            } else {
                // 上传并返回访问地址
                String url = sysFileService.uploadFile(file);
                SystemFileDTO sysFile = new SystemFileDTO();
                sysFile.setName(FileUtil.getName(url));
                sysFile.setOriginalName(file.getOriginalFilename());
                sysFile.setUrl(domain + url);
                // 本地存储的相对路径（system_file_info.image_path NOT NULL，OSS 分支同义字段）
                sysFile.setPath(url);
                String id = saveFileInfo(sysFile, file, uploadType, module, sourceId, sourceStage, type);
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
     * @param file        文件
     * @param module      模块
     * @param uploadType  上传类型  OSS LOCAL MINIO
     * @param sourceId    数据ID
     * @param sourceStage 数据的阶段，提交/审批/验收，备用，默认为INIT
     * @param type        类型，是文件还是图片
     */
    private String saveFileInfo(SystemFileDTO sysFile, MultipartFile file, String uploadType, String module, String sourceId, String sourceStage, String type) {
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
        systemFileInfo.setFileSize(file.getSize());
        systemFileInfo.setFileStoreType(uploadType);
        systemFileInfo.setDepartmentId(SecurityUtils.getDepartmentId());
        systemFileInfo.setImagePath(sysFile.getPath());
        systemFileInfo.setImageUrl(sysFile.getUrl());
        systemFileInfo.setNewName(FileUtil.getName(sysFile.getUrl()));
        systemFileInfo.setOriginalName(file.getOriginalFilename());
        systemFileInfo.setOrganizationId(SecurityUtils.getOrganizationId());
        systemFileInfoService.insertSystemFileInfo(systemFileInfo);
        return systemFileInfo.getId();
    }


}
