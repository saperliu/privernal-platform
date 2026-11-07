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

package com.privernal.dynamic.datav.service;

import com.privernal.dynamic.datav.domain.DynamicDatavFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV 上传文件服务
 * <p>
 * 只做「存字节 / 取字节 / 跟着数据集一起消失」三件事：解析与列推断属于连接器，
 * 校验属于这一层（弹窗与 API 直调必须撞同一堵墙）。
 *
 * @author saperliu
 */
public interface IDynamicDatavFileService {

    /**
     * 落库一份上传内容；扩展名与字节数在这里判，不看浏览器给的 MIME
     *
     * @param datasetCode 已知所属数据集时传入；先上传后建数据集时可为空，保存数据集时回填
     * @return 落库后的文件（content 已置空，不回传字节）
     */
    DynamicDatavFile store(MultipartFile file, String datasetCode);

    /**
     * 按文件编码取原始字节，取不到返回 null（不存在、已删除或不属于当前组织都走这一条）
     */
    byte[] readContent(String fileCode);

    /**
     * 把先上传的文件绑到数据集上，绑不上时静默返回 0
     */
    int bindDataset(String fileCode, String datasetCode);

    /**
     * 数据集不再引用这个文件时清掉；按 fileCode 定位，跨组织删不到别人的行
     */
    int deleteByFileCode(String fileCode);

    /**
     * 删除数据集时连带清理其文件
     */
    int deleteByDatasetCode(String datasetCode);
}
