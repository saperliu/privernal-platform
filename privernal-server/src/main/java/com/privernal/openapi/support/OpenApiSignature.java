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

package com.privernal.openapi.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * OpenAPI 签名规范串与 HMAC 计算，客户端文档需与此严格一致。
 * <p>
 * canonical = METHOD \n PATH \n TIMESTAMP \n NONCE \n BODY_DIGEST
 * X-Signature = hex(hmac_sha256(secret, canonical))
 * BODY_DIGEST = hex(sha256(rawBody))，无请求体时对空串取摘要。
 */
public class OpenApiSignature {

    public static final String HEADER_ACCESS_KEY = "X-Access-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_BODY_DIGEST = "X-Body-Digest";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private OpenApiSignature() {
    }

    public static String buildCanonical(String method, String path, String timestamp, String nonce, String bodyDigest) {
        return method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyDigest;
    }

    public static String hmacSha256Hex(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public static String sha256Hex(byte[] body) {
        try {
            byte[] input = body == null ? new byte[0] : body;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
