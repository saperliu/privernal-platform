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

import com.privernal.apppackage.dto.AppPackageDTO;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 包签名（R2-14 ②）最不能被改坏的三件事：
 * 签过以后改包必须被验出来（防篡改是签名的存在理由）；canonical 与字段顺序/空白无关
 * （UI 下载时会 JSON.stringify 重排版，验签必须不受影响）；无主密钥环境只降级为「无法校验」
 * 而不是假装验过。
 *
 * @author saperliu
 */
@DisplayName("应用包签名测试")
class PackageSignaturesTest {

    private static AppPackageDTO samplePackage() {
        AppPackageDTO pkg = new AppPackageDTO();
        pkg.getApp().setCode("demo_app");
        pkg.getApp().setAppName("演示应用");
        AppPackageDTO.Form form = new AppPackageDTO.Form();
        form.setTableCode("demo_order");
        form.setTableName("订单");
        pkg.getForms().add(form);
        AppPackageDTO.DictTypePackage dict = new AppPackageDTO.DictTypePackage();
        dict.setDictType("demo_order_status");
        dict.setDictName("订单状态");
        AppPackageDTO.DictDataPackage data = new AppPackageDTO.DictDataPackage();
        data.setDictValue("1");
        data.setDictLabel("待处理");
        dict.getDataList().add(data);
        pkg.getDicts().add(dict);
        return pkg;
    }

    @Test
    @DisplayName("签名→验签闭环：原包 VERIFIED 且校验和一致")
    void signThenVerifyRoundtrip() {
        PackageSignatures signatures = new PackageSignatures("test-master-key");
        AppPackageDTO pkg = samplePackage();
        signatures.sign(pkg);

        assertThat(pkg.getSignature()).isNotBlank();
        assertThat(pkg.getSignatureAlgorithm()).isEqualTo(PackageSignatures.ALGORITHM);
        assertThat(pkg.getChecksum()).isNotBlank();

        PackageSignatures.Verification verification = signatures.verify(pkg);
        assertThat(verification.status()).isEqualTo(PackageSignatures.Status.VERIFIED);
        assertThat(verification.checksumMatch()).isTrue();
    }

    @Test
    @DisplayName("签名后改包内容 → MISMATCH + 校验和不符（防篡改是签名的存在理由）")
    void tamperedPackageIsRejected() {
        PackageSignatures signatures = new PackageSignatures("test-master-key");
        AppPackageDTO pkg = samplePackage();
        signatures.sign(pkg);
        pkg.getForms().get(0).setTableName("改过的名字");

        PackageSignatures.Verification verification = signatures.verify(pkg);
        assertThat(verification.status()).isEqualTo(PackageSignatures.Status.MISMATCH);
        assertThat(verification.checksumMatch()).isFalse();
    }

    @Test
    @DisplayName("换主密钥验签 → MISMATCH（签名密钥随环境走）")
    void differentKeyIsMismatch() {
        AppPackageDTO pkg = samplePackage();
        new PackageSignatures("key-a").sign(pkg);

        PackageSignatures.Verification verification = new PackageSignatures("key-b").verify(pkg);
        assertThat(verification.status()).isEqualTo(PackageSignatures.Status.MISMATCH);
    }

    @Test
    @DisplayName("canonical 与字段顺序/空白无关：重排版后再验签仍通过")
    void canonicalSurvivesReserialization() {
        PackageSignatures signatures = new PackageSignatures("test-master-key");
        AppPackageDTO pkg = samplePackage();
        signatures.sign(pkg);

        // 模拟 UI 下载再上传：JSON 往返 + fastjson2 重序列化（字段顺序可能改变）
        AppPackageDTO roundTripped = JSON.parseObject(JSON.toJSONString(pkg), AppPackageDTO.class);
        PackageSignatures.Verification verification = signatures.verify(roundTripped);
        assertThat(verification.status()).isEqualTo(PackageSignatures.Status.VERIFIED);
        assertThat(verification.checksumMatch()).isTrue();
    }

    @Test
    @DisplayName("未配主密钥导出：只带校验和不签名，验签=NOT_SIGNED 而非 VERIFIED")
    void unsignedWhenKeyMissing() {
        PackageSignatures signatures = new PackageSignatures("");
        AppPackageDTO pkg = samplePackage();
        signatures.sign(pkg);

        assertThat(signatures.keyConfigured()).isFalse();
        assertThat(pkg.getChecksum()).isNotBlank();
        assertThat(pkg.getSignature()).isNull();

        PackageSignatures.Verification verification = signatures.verify(pkg);
        assertThat(verification.status()).isEqualTo(PackageSignatures.Status.NOT_SIGNED);
        // checksum 与签名体系无关，未签名也能查出「包被改过」
        pkg.getForms().get(0).setTableName("篡改");
        assertThat(signatures.verify(pkg).checksumMatch()).isFalse();
    }

    @Test
    @DisplayName("已签包落到未配主密钥的环境 → KEY_MISSING（无法校验，不假装验过）")
    void signedPackageOnKeylessEnvironment() {
        AppPackageDTO pkg = samplePackage();
        new PackageSignatures("test-master-key").sign(pkg);

        PackageSignatures.Verification verification = new PackageSignatures("").verify(pkg);
        assertThat(verification.status()).isEqualTo(PackageSignatures.Status.KEY_MISSING);
    }

    @Test
    @DisplayName("canonical 剔除 signature 自身：重复 sign 覆盖旧签名不产生自引用")
    void resignOverwritesCleanly() {
        PackageSignatures signatures = new PackageSignatures("test-master-key");
        AppPackageDTO pkg = samplePackage();
        signatures.sign(pkg);
        String first = pkg.getSignature();
        signatures.sign(pkg);
        assertThat(pkg.getSignature()).isEqualTo(first);
    }
}
