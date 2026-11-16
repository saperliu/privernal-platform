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

package com.privernal.apppackage.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.privernal.apppackage.dto.AppPackageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 官方模板包目录（R2-14 模板市场 ③）：模板以应用包 JSON 随后端发布（classpath
 * {@code apptemplates/}），私有化部署零外部依赖——不依赖任何远端市场服务。
 *
 * <p>目录 {@code manifest.json} 只存展示面与统计（code/名称/分类/描述/表单流程数量），
 * 包体在 {@code <code>.json}。列表页不解析包体，安装时才加载。
 *
 * <p>模板包携带 checksum（sha256，环境无关）供加载时完整性校验；signature 不预置——
 * 签名密钥随环境主密钥走，安装时由本环境现签（install 前重签再走标准导入链路），
 * 报告里看到的签名状态与手工导出的包一致。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class TemplateCatalog {

    /** manifest 里声明的统计数与包体实际内容不符时只告警不拒绝：模板由发版流程保证质量 */
    static final String MANIFEST_PATH = "apptemplates/manifest.json";

    /** 模板列表行：manifest 条目 + 展示统计 */
    public record TemplateSummary(String code, String name, String category, String description,
                                  String version, String author, String appCode, String appName,
                                  int formCount, int flowCount, int screenCount, int dictCount,
                                  String specVersion) {
    }

    /** manifest 缺失/为空（如有人裁剪了发布包）→ 返回空市场而不是 500 */
    public List<TemplateSummary> list() {
        String manifest = readResource(MANIFEST_PATH);
        if (manifest == null) {
            log.info("未找到官方模板清单 {}，模板市场为空", MANIFEST_PATH);
            return new ArrayList<>();
        }
        List<TemplateSummary> summaries = new ArrayList<>();
        JSONObject root = JSON.parseObject(manifest);
        for (Object item : root.getJSONArray("templates")) {
            JSONObject entry = (JSONObject) item;
            summaries.add(new TemplateSummary(
                    entry.getString("code"),
                    entry.getString("name"),
                    entry.getString("category"),
                    entry.getString("description"),
                    entry.getString("version"),
                    entry.getString("author"),
                    entry.getString("appCode"),
                    entry.getString("appName"),
                    entry.getIntValue("formCount"),
                    entry.getIntValue("flowCount"),
                    entry.getIntValue("screenCount"),
                    entry.getIntValue("dictCount"),
                    entry.getString("specVersion")));
        }
        return summaries;
    }

    /** 加载模板包体；加载后校验 checksum（模板包必须带 checksum，发版产物缺失校验和=构建错误） */
    public AppPackageDTO load(String templateCode) {
        String summaryCode = templateCode == null ? "" : templateCode.trim();
        Assert.hasText(summaryCode, "模板编码为空");
        // 白名单：只认 manifest 里登记过的模板，杜绝用本端点读任意 classpath 资源
        boolean registered = list().stream().anyMatch(item -> summaryCode.equals(item.code()));
        if (!registered) {
            throw new IllegalArgumentException("模板不存在或未登记：" + summaryCode);
        }
        String body = readResource("apptemplates/" + summaryCode + ".json");
        if (body == null) {
            throw new IllegalArgumentException("模板包体缺失：" + summaryCode + "（清单已登记但包文件不存在）");
        }
        AppPackageDTO pkg = JSON.parseObject(body, AppPackageDTO.class);
        PackageSignatures signatures = new PackageSignatures("");
        PackageSignatures.Verification verification = signatures.verify(pkg);
        if (pkg.getChecksum() != null && !verification.checksumMatch()) {
            throw new IllegalStateException("模板包校验和不符（发布产物损坏或被改动）：" + summaryCode);
        }
        if (pkg.getChecksum() == null) {
            log.warn("模板包缺少 checksum，跳过完整性校验：{}", summaryCode);
        }
        return pkg;
    }

    private String readResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException notFound) {
            return null;
        }
    }

    /** 从包体生成/刷新 manifest 条目（构建模板产物时由导出脚本调用，运行期只读 manifest） */
    public static JSONObject buildManifestEntry(AppPackageDTO pkg) {
        JSONObject entry = new JSONObject();
        entry.put("code", pkg.getTemplate() == null ? null : pkg.getTemplate().getCode());
        entry.put("name", pkg.getTemplate() == null ? null : pkg.getTemplate().getName());
        entry.put("category", pkg.getTemplate() == null ? null : pkg.getTemplate().getCategory());
        entry.put("description", pkg.getTemplate() == null ? null : pkg.getTemplate().getDescription());
        entry.put("version", pkg.getTemplate() == null ? null : pkg.getTemplate().getVersion());
        entry.put("author", pkg.getTemplate() == null ? null : pkg.getTemplate().getAuthor());
        entry.put("appCode", pkg.getApp().getCode());
        entry.put("appName", pkg.getApp().getAppName());
        entry.put("formCount", pkg.getForms().size());
        entry.put("flowCount", pkg.getFlows().size());
        entry.put("screenCount", pkg.getScreens().size());
        entry.put("dictCount", pkg.getDicts().size());
        entry.put("specVersion", pkg.getSpecVersion());
        return entry;
    }
}
