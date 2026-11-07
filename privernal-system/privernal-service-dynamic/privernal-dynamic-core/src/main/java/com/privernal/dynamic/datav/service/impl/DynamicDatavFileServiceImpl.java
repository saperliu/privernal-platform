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

package com.privernal.dynamic.datav.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.IdUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.datav.domain.DynamicDatavFile;
import com.privernal.dynamic.datav.mapper.DynamicDatavFileMapper;
import com.privernal.dynamic.datav.service.IDynamicDatavFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CSV 上传文件服务实现
 * <p>
 * 准入只看两件事：扩展名与字节数。浏览器给的 MIME 一律不参与判定——上传框里选中的
 * .csv 常常报 application/octet-stream，按 MIME 拦会把合法文件挡在外面；而按 MIME 放
 * 又等于让提交方自证清白。真正的防线是这份字节只交给 {@code CsvParser} 解析，
 * 既不落盘也不执行、不渲染。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DynamicDatavFileServiceImpl implements IDynamicDatavFileService {

    /** 与 HTTP 取数同源的上限（HttpSourceConnector 的响应体硬上限 2MB）：两条传输路径共用一个天花板 */
    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "txt");

    /** 展示名里不许留控制字符：它会进日志、进列表标题，换行能把一条日志写成两条 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");

    @Autowired
    private DynamicDatavFileMapper fileMapper;

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDatavFile store(MultipartFile file, String datasetCode) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的 CSV 文件");
        }
        requireAllowedExtension(file.getOriginalFilename());
        requireWithinSizeLimit(file.getSize());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败，请重新选择后重试");
        }
        // 上限按落库字节数再判一次：getSize() 来自请求声明，与真正读到的字节可能不一致
        requireWithinSizeLimit(content.length);

        DynamicDatavFile entity = new DynamicDatavFile();
        entity.setFileCode("csv_" + IdUtils.simpleUUID());
        entity.setDatasetCode(StringUtils.trimToNull(datasetCode));
        entity.setFileName(displayName(file.getOriginalFilename()));
        entity.setSizeBytes((long) content.length);
        entity.setContent(content);
        entity.setOrganizationId(SecurityUtils.getOrganizationId());
        entity.setDepartmentId(SecurityUtils.getDepartmentId());
        entity.setCreatedBy(SecurityUtils.getUserId());
        entity.setCreatedTime(new Date());
        fileMapper.insert(entity);
        // 字节不回传：接口响应里带一份 CSV 既没有消费方，又会把内容送进日志与浏览器历史
        entity.setContent(null);
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] readContent(String fileCode) {
        if (StringUtils.isBlank(fileCode)) {
            return null;
        }
        // 组织隔离与逻辑删除由平台拦截器附加（organization_id = 当前组织、deleted_flag = 'N'），
        // 所以「按不透明 fileId 直读」不会变成跨组织 IDOR：别人的编码在这里就是查不到行。
        LambdaPrivernalWrapper<DynamicDatavFile> wrapper = new LambdaPrivernalWrapper<>();
        // content 在实体上是 select=false，取字节这条路径必须显式把它请回来
        wrapper.select(DynamicDatavFile::getContent);
        wrapper.eq(DynamicDatavFile::getFileCode, fileCode);
        DynamicDatavFile file = fileMapper.selectOne(wrapper);
        return file == null ? null : file.getContent();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int bindDataset(String fileCode, String datasetCode) {
        if (StringUtils.isBlank(fileCode) || StringUtils.isBlank(datasetCode)) {
            return 0;
        }
        DynamicDatavFile update = new DynamicDatavFile();
        update.setDatasetCode(datasetCode);
        LambdaPrivernalWrapper<DynamicDatavFile> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavFile::getFileCode, fileCode);
        return fileMapper.update(update, wrapper);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteByFileCode(String fileCode) {
        if (StringUtils.isBlank(fileCode)) {
            return 0;
        }
        LambdaPrivernalWrapper<DynamicDatavFile> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavFile::getFileCode, fileCode);
        return fileMapper.delete(wrapper);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteByDatasetCode(String datasetCode) {
        if (StringUtils.isBlank(datasetCode)) {
            return 0;
        }
        LambdaPrivernalWrapper<DynamicDatavFile> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavFile::getDatasetCode, datasetCode);
        return fileMapper.delete(wrapper);
    }

    // ---------------- 校验（纯函数，包级可见以便离线回归） ----------------

    static String extensionOf(String fileName) {
        String lower = StringUtils.trimToEmpty(fileName).toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        // 没有点、点在首位（.csv 这种隐藏文件名）都不算合法扩展名
        return dot <= 0 ? "" : lower.substring(dot + 1);
    }

    static void requireAllowedExtension(String fileName) {
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException("CSV 数据源只接受 .csv 或 .txt 文件");
        }
    }

    static void requireWithinSizeLimit(long size) {
        if (size > MAX_UPLOAD_BYTES) {
            throw new ServiceException("上传文件超过 " + (MAX_UPLOAD_BYTES / 1024 / 1024) + "MB 上限");
        }
    }

    /** 展示名：剥掉可能带来的路径与控制字符，长度截到列宽 */
    static String displayName(String fileName) {
        String name = StringUtils.trimToEmpty(fileName);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = CONTROL_CHARS.matcher(name).replaceAll("");
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return name.isEmpty() ? "data.csv" : name;
    }
}
