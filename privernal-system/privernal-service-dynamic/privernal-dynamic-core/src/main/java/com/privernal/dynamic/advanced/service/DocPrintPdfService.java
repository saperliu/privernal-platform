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

package com.privernal.dynamic.advanced.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 单据打印 PDF 归档（plan-sheet-print §3.5/§5 M4）。
 * <p>
 * 输入是前端渲染层（utils/doc-print/render.ts）产出的"渲染后 HTML + 打印样式"——
 * 与浏览器打印同一份内容，保证两条通道版式一致（M4 验收项）。
 * <ul>
 *   <li>jsoup（MIT）做 well-formed XHTML 归一化：openhtmltopdf 的 XML 解析器
 *       对未闭合标签直接报错，富文本编辑器产出的 HTML 必须先过一道；</li>
 *   <li>中文字体显式注册（交付镜像内置 Noto CJK，OFL）——Linux 容器无默认中文字体，
 *       不注册必乱码；注册族名固定 DocPrintCJK 并以 !important 覆写 body，
 *       与字体文件本身叫什么解耦；</li>
 *   <li>真页码（第 X 页 / 共 Y 页）：浏览器通道做不到（§2 体验边界），PDF 通道
 *       用 @page @bottom-center + counter(page)/counter(pages) 实现。</li>
 * </ul>
 */
@Slf4j
@Service
public class DocPrintPdfService {

    /** PDF 输出统一字体族（注册名与 CSS 覆写互为锚点） */
    static final String FONT_FAMILY = "DocPrintCJK";

    private static final String PAGE_NUMBER_CSS =
            "@page { @bottom-center { content: \"第 \" counter(page) \" 页 / 共 \" counter(pages) \" 页\"; "
                    + "font-family: " + FONT_FAMILY + "; font-size: 10px; color: #666; } }";

    /** 字体文件（.ttf；TTC/OTF(CFF) 均不受本链路支持——pdfbox 2.0 的 TTF 装载路径会静默失败）。
     *  显式配置优先，用于交付环境指定专用字体 */
    @Value("${dynamic.print.pdf.font-path:}")
    private String configuredFontPath;

    /** 随包内置的默认字体：把可分发的中文字体放进 resources/fonts 即开箱可用，无需配置 */
    private static final List<String> CLASSPATH_FONTS = List.of(
            "/fonts/docprint.ttf",
            "/fonts/NotoSansSC-Regular.ttf");

    /** 本机常见中文字体（仅 .ttf；TTC/OTF 会被跳过）。交付镜像没内置时也能就地取用 */
    private static final List<String> FILE_CANDIDATES = List.of(
            "/usr/share/fonts/truetype/docprint/docprint.ttf",
            "/usr/share/fonts/docprint/docprint.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansSC-Regular.ttf",
            "/usr/share/fonts/arphic/arialuni.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/Library/Fonts/Arial Unicode.ttf");

    /** 渲染 HTML 为 PDF 字节 */
    public byte[] renderPdf(String printHtml) {
        String xhtml = toXhtml(printHtml);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            registerFont(builder);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("单据打印 PDF 渲染失败", e);
            throw new com.privernal.common.core.exception.ServiceException("PDF 渲染失败：" + e.getMessage());
        }
    }

    /** jsoup XHTML 归一化 + 页码 CSS 注入 + 字体覆写 */
    private String toXhtml(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                .charset("UTF-8");
        org.jsoup.nodes.Element head = doc.head();
        if (head == null) {
            throw new com.privernal.common.core.exception.ServiceException("打印 HTML 缺少 <head>，无法出 PDF");
        }
        head.appendElement("style").text(
                PAGE_NUMBER_CSS + "\nbody { font-family: " + FONT_FAMILY + ", sans-serif !important; }");
        return doc.html();
    }

    /**
     * 解析并注册中文字体：显式配置 → 随包内置（classpath:/fonts/*.ttf）→ 本机常见字体。
     * <p>
     * 三者都拿不到时**直接失败**，不再"照常出一份 PDF"：实测未注册字体时
     * openhtmltopdf 仍返回 200 与合法的 %PDF 文件，只是中文全部豆腐块——这种"成功但不可读"
     * 的产物会被当成可用归档件交付，比报错危险得多。
     */
    private void registerFont(PdfRendererBuilder builder) {
        List<String> skipped = new ArrayList<>();
        if (configuredFontPath != null && !configuredFontPath.isBlank()) {
            byte[] bytes = readFile(configuredFontPath.trim(), skipped);
            if (bytes != null) {
                useFont(builder, bytes, configuredFontPath.trim());
                return;
            }
        }
        for (String resource : CLASSPATH_FONTS) {
            byte[] bytes = readClasspath(resource, skipped);
            if (bytes != null) {
                useFont(builder, bytes, "classpath:" + resource);
                return;
            }
        }
        for (String path : FILE_CANDIDATES) {
            byte[] bytes = readFile(path, skipped);
            if (bytes != null) {
                useFont(builder, bytes, path);
                return;
            }
        }
        throw new com.privernal.common.core.exception.ServiceException(
                "未找到可用的中文字体（本链路只支持 .ttf）"
                        + (skipped.isEmpty() ? "" : "；已跳过：" + String.join("、", skipped))
                        + "。请把可分发的中文字体（如 Noto Sans SC / 思源黑体，OFL）放到 classpath:/fonts/docprint.ttf，"
                        + "或用 dynamic.print.pdf.font-path 指向 .ttf 文件");
    }

    private void useFont(PdfRendererBuilder builder, byte[] bytes, String source) {
        builder.useFont(() -> new ByteArrayInputStream(bytes), FONT_FAMILY);
        log.info("单据打印 PDF 字体已注册：{} → {}", source, FONT_FAMILY);
    }

    private byte[] readFile(String path, List<String> skipped) {
        File file = new File(path);
        if (!file.isFile() || file.length() == 0) {
            return null;
        }
        if (!path.toLowerCase().endsWith(".ttf")) {
            // pdfbox 2.0 的字体装载只认 sfnt 1.0（.ttf）：TTC 集合与 OTF/CFF（'OTTO'）
            // 会静默装载失败并回落西文基础字体（中文豆腐块）——离线探针实测（2026-10-05）
            skipped.add(path + "（非 .ttf）");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("字体读取失败，继续尝试下一个候选：{}", path, e);
            skipped.add(path + "（读取失败）");
            return null;
        }
    }

    private byte[] readClasspath(String resource, List<String> skipped) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("classpath 字体读取失败：{}", resource, e);
            skipped.add("classpath:" + resource + "（读取失败）");
            return null;
        }
    }
}
