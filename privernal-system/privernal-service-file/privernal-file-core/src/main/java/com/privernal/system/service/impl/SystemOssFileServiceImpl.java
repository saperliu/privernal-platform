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

package com.privernal.system.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.file.MimeTypeUtils;
import cn.hutool.core.io.FileUtil;
import com.privernal.system.config.OssConfig;
import com.privernal.system.constant.UploadConstants;
import com.privernal.system.dto.SystemFileDTO;
import com.privernal.system.service.ISystemOssFileService;
import com.privernal.system.utils.FileUploadUtils;
import com.aliyun.oss.*;
import com.aliyun.oss.internal.OSSHeaders;
import com.aliyun.oss.model.*;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-12-19 17:50
 * @Description
 */
@Service
@Slf4j
public class SystemOssFileServiceImpl implements ISystemOssFileService {

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private MinioClient client;

    /**
     * 资源上传使用的类型
     */
    @Value("${privernal.upload.type}")
    public String uploadType;

    @Override
    public SystemFileDTO uploadFile(MultipartFile file, String filePath) throws Exception {
        // 对象存储不落盘、不经过 FileUploadUtils.upload，必须在这里补同一份
        // 名称/大小/扩展名白名单校验（S13：此前上传全程无白名单）
        FileUploadUtils.assertAllowed(file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        if (UploadConstants.MINIO.equals(uploadType)) {
            String netUrl = uploadFile(file);
            SystemFileDTO systemFileDTO = new SystemFileDTO();
            systemFileDTO.setName(FileUtil.getName(netUrl));
            systemFileDTO.setOriginalName(file.getOriginalFilename());
            systemFileDTO.setUrl(netUrl);
            systemFileDTO.setPath(netUrl);
            return systemFileDTO;
        }
        return saveByteFile(file, ossConfig.getBucket(), ossConfig.getPath(), file.getOriginalFilename(), filePath);
    }

    /**
     * 本地文件上传接口
     *
     * @param file 上传的文件
     * @return 访问地址
     * @throws Exception
     */

    private String uploadFile(MultipartFile file) throws Exception {
        String fileName = FileUploadUtils.extractFilename(file);
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket(ossConfig.getBucket())
                .object(fileName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build();
        client.putObject(args);
        return ossConfig.getBucket() + "/" + fileName;
    }

    @Override
    public OSSObject downLoadImage(String fileName) {
        // S14：下载键走平台生成口径（日期目录/对象名.扩展名），堵住任意键枚举与路径穿越
        checkDownloadKey(fileName);
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(), ossConfig.getAccessKeySecret());
        Date expiration = new Date(System.currentTimeMillis() + 3600 * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(ossConfig.getBucket(), fileName, HttpMethod.GET);
        // 设置过期时间。
        request.setExpiration(expiration);
        // 生成签名URL（HTTP GET请求）。
        URL signedUrl = ossClient.generatePresignedUrl(request);
        // 使用签名URL发送请求。
        Map<String, String> customHeaders = new HashMap<String, String>();
        // 添加GetObject请求头。
        //customHeaders.put("Range", "bytes=100-1000");
        OSSObject object = ossClient.getObject(signedUrl, customHeaders);
        return object;
    }


    private SystemFileDTO saveByteFile(MultipartFile file, String bucket, String path, String fileName, String filePath) {
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(), ossConfig.getAccessKeySecret());

        SystemFileDTO systemFileDTO = new SystemFileDTO();
        // 填写字符串。
        //        String content = "Hello OSS";
        // 创建PutObjectRequest对象。
        // 依次填写Bucket名称（例如examplebucket）和Object完整路径（例如exampledir/exampleobject.txt）。Object完整路径中不能包含Bucket名称。
        try {
            String netUrl = path + "/" + FileUploadUtils.extractFilename(file);
            if (StringUtils.isNotEmpty(filePath)) {
                netUrl = path + "/" + filePath + "/" + FileUploadUtils.extractFilename(file);
            }
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, netUrl, new ByteArrayInputStream(file.getBytes()));

            // 如果需要上传时设置存储类型和访问权限，请参考以下示例代码。
            // ObjectMetadata metadata = new ObjectMetadata();
            // metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
            // metadata.setObjectAcl(CannedAccessControlList.Private);
            ObjectMetadata metadata = new ObjectMetadata();
            log.info(" fileName {}   netUrl {}", fileName, netUrl);
            metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
            metadata.setContentType(getContentType("." + FileUploadUtils.getExtension(file)));

            putObjectRequest.setMetadata(metadata);

            // 上传字符串。
            ossClient.putObject(putObjectRequest);
            systemFileDTO.setName(FileUtil.getName(netUrl));
            systemFileDTO.setOriginalName(file.getOriginalFilename());
            systemFileDTO.setUrl(netUrl);
            systemFileDTO.setPath(netUrl);
        } catch (Exception e) {
            log.error("save file error", e);
        } finally {
            // 关闭OSSClient。
            ossClient.shutdown();
        }
        return systemFileDTO;
    }

    /**
     * 下载键约束（S14）：拒绝路径穿越，且必须含平台生成的日期段（{@code …/yyyy/MM/dd/名字.扩展名}）。
     * 对象键由 {@link FileUploadUtils#extractFilename} 生成，总有日期目录；没有日期段的键
     * 不是本服务写进去的，不允许借下载口探测。
     */
    private static final java.util.regex.Pattern DOWNLOAD_KEY_SHAPE =
            java.util.regex.Pattern.compile("^/?[\\w.-]+/\\d{4}/\\d{1,2}/\\d{1,2}/[^/]+$");

    private static void checkDownloadKey(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            throw new ServiceException("下载地址不能为空");
        }
        if (fileName.contains("..") || fileName.contains("\\") || fileName.contains("\0")) {
            throw new ServiceException("下载地址不合法");
        }
        if (!DOWNLOAD_KEY_SHAPE.matcher(fileName).matches()) {
            throw new ServiceException("下载地址不在可下载范围内");
        }
    }

    public static String getContentType(String fileNameExtension) {
        if (fileNameExtension.equalsIgnoreCase(".bmp")) {
            return "image/bmp";
        }
        if (fileNameExtension.equalsIgnoreCase(".gif")) {
            return "image/gif";
        }
        if (fileNameExtension.equalsIgnoreCase(".jpeg") ||
                fileNameExtension.equalsIgnoreCase(".jpg") ||
                fileNameExtension.equalsIgnoreCase(".png")) {
            return "image/jpg";
        }
        if (fileNameExtension.equalsIgnoreCase(".html") || fileNameExtension.equalsIgnoreCase(".htm")) {
            // 不给 text/html：/statics 与上传域同源，内联渲染即存储型 XSS；强制按附件流处理
            return "application/octet-stream";
        }
        if (fileNameExtension.equalsIgnoreCase(".txt")) {
            return "text/plain";
        }
        if (fileNameExtension.equalsIgnoreCase(".vsd")) {
            return "application/vnd.visio";
        }
        if (fileNameExtension.equalsIgnoreCase(".pptx") ||
                fileNameExtension.equalsIgnoreCase(".ppt")) {
            return "application/vnd.ms-powerpoint";
        }
        if (fileNameExtension.equalsIgnoreCase(".docx") ||
                fileNameExtension.equalsIgnoreCase(".doc")) {
            return "application/msword";
        }
        if (fileNameExtension.equalsIgnoreCase(".xml")) {
            return "text/xml";
        }
        return "image/jpg";
    }

    @Override
    public SystemFileDTO deleteImage(List<String> fileNames) {
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(), ossConfig.getAccessKeySecret());
        try {
            // 删除文件。
            // 填写需要删除的多个文件完整路径。文件完整路径中不能包含Bucket名称。
            DeleteObjectsResult deleteObjectsResult = ossClient.deleteObjects(new DeleteObjectsRequest(ossConfig.getBucket()).withKeys(fileNames).withEncodingType("url"));
            List<String> deletedObjects = deleteObjectsResult.getDeletedObjects();
            for (String obj : deletedObjects) {
                String deleteObj = URLDecoder.decode(obj, StandardCharsets.UTF_8);
                log.info("delete file  {}", deleteObj);
            }
        } catch (OSSException oe) {
            log.error("Error Message:{} Error Code: {} Request ID:   {}   Host ID:{}", oe.getErrorMessage(), oe.getErrorCode(), oe.getRequestId(), oe.getHostId());
        } catch (ClientException ce) {
            log.error("Error Message: {}", ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
        return null;
    }
}
