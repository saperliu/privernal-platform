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

package com.privernal.dynamic.datav.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.privernal.dynamic.dba.annotation.Column;
import com.privernal.dynamic.dba.annotation.Table;
import com.privernal.dynamic.dba.constants.MySqlTypeConstant;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * CSV 上传文件 dynamic_datav_file
 * <p>
 * 存字节而不是存「文件模块里的路径」：privernal-dynamic-core 不依赖 privernal-file-api，为一张 CSV 表
 * 跨模块加依赖不划算，而独立表顺手解决了生命周期（跟着数据集一起删）与应用包携带数据。
 * <p>
 * {@code content} 只交给 CsvParser 解析：不落盘、不执行、不渲染，所以浏览器报的 MIME
 * 是什么都不影响安全性，它也不参与任何准入判定（见服务里的校验口径）。
 *
 * @author saperliu
 */
@Table(name = "dynamic_datav_file")
@TableName("dynamic_datav_file")
@Data
public class DynamicDatavFile extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Column(name = "id", type = MySqlTypeConstant.VARCHAR, isKey = true, length = 36)
    private String id;

    @Column(name = "department_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String departmentId;

    @Column(name = "organization_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String organizationId;

    /**
     * 对外即 sourceConfig.fileId；不含文件名与路径，因此可以安全进日志
     */
    @Column(name = "file_code", type = MySqlTypeConstant.VARCHAR, length = 64)
    private String fileCode;

    /**
     * 先上传后保存数据集时为空，保存数据集时回填
     */
    @Column(name = "dataset_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String datasetCode;

    /**
     * 仅作展示；禁止参与路径拼接或作为下载名
     */
    @Column(name = "file_name", type = MySqlTypeConstant.VARCHAR, length = 255)
    private String fileName;

    @Column(name = "size_bytes", type = MySqlTypeConstant.BIGINT, length = 20)
    private Long sizeBytes;

    /**
     * 不查询列表时不该把它带上：一行就是几百 KB，select 全表等于把堆交给上传量
     */
    @TableField(select = false)
    @Column(name = "content", type = "longblob")
    private byte[] content;
}
