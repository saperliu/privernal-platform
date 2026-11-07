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

package com.privernal.system.feign;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.dto.SystemFileDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author saperliu
 * @date 2022-04-10
 * @Description 文件服务
 */
public interface RemoteFileService {
    /**
     * 上传文件
     *
     * @param file 文件信息
     * @return 结果
     */
    @PostMapping(value = "/inner/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<SystemFileDTO> upload(@RequestPart(value = "file") MultipartFile file, @RequestParam("module") String module, @RequestParam(value = "filePath", required = false) String filePath, @RequestParam("sourceId") String orderId, @RequestParam("sourceStage") String orderStage, @RequestParam("type") String type, @RequestHeader(Constants.SERVICE_REQUEST) String request);
}
