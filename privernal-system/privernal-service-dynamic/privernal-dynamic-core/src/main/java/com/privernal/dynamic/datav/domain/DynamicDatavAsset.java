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
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 大屏素材 dynamic_datav_asset
 * <p>
 * 只存元数据：文件字节走平台的文件服务（本地/OSS/MinIO 三后端），本表登记
 * 文件服务返回的可访问 URL 与分类。上传是前端两步走（先 /file/back/file/upload 拿 URL，
 * 再 /back/datav/asset/save 登记），privernal-dynamic-core 因此不依赖 privernal-file-api。
 * <p>
 * 删除有引用保护：file_url 以 LIKE 方式扫 dynamic_datav_screen.schema_json，
 * 仍被引用的素材拒绝删除（见服务实现）。文件服务里的对象不随本表删除——
 * 三后端里只有 OSS 有删除接口，跨后端清理是运维动作（与 CSV 文件的口径一致）。
 *
 * @author saperliu
 */
@Table(name = "dynamic_datav_asset")
@TableName("dynamic_datav_asset")
@Data
public class DynamicDatavAsset extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Column(name = "id", type = MySqlTypeConstant.VARCHAR, isKey = true, length = 36)
    private String id;

    @Column(name = "department_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String departmentId;

    @Column(name = "organization_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String organizationId;

    /**
     * 对外业务主键；不含文件名与路径，可以安全进日志
     */
    @Column(name = "asset_code", type = MySqlTypeConstant.VARCHAR, length = 64)
    private String assetCode;

    @Column(name = "asset_name", type = MySqlTypeConstant.VARCHAR, length = 200)
    private String assetName;

    /**
     * 业务分类（如 背景 / 边框 / 视频），自由文本，缺省「通用」
     */
    @Column(name = "category", type = MySqlTypeConstant.VARCHAR, length = 50)
    private String category;

    /**
     * image | video；决定素材面板里的展示形态，不参与任何准入判定
     */
    @Column(name = "asset_type", type = MySqlTypeConstant.VARCHAR, length = 20)
    private String assetType;

    /**
     * 文件服务返回的可访问 URL（/statics/... 或 OSS 域名）；大屏 schema 里引用的就是它
     */
    @Column(name = "file_url", type = MySqlTypeConstant.VARCHAR, length = 500)
    private String fileUrl;

    /**
     * 仅作展示与检索；禁止参与路径拼接或作为下载名
     */
    @Column(name = "original_name", type = MySqlTypeConstant.VARCHAR, length = 255)
    private String originalName;

    @Column(name = "file_size", type = MySqlTypeConstant.BIGINT, length = 20)
    private Long fileSize;
}
