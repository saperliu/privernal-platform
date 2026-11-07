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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.advanced.domain.DynamicFormShare;
import com.privernal.dynamic.advanced.domain.DynamicShareAccessLog;
import com.privernal.dynamic.advanced.domain.DynamicShareReceipt;
import com.privernal.dynamic.advanced.engine.DataWriteAttributes;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.engine.LinkageIdemUtils;
import com.privernal.dynamic.advanced.mapper.DynamicFormShareMapper;
import com.privernal.dynamic.advanced.mapper.DynamicShareAccessLogMapper;
import com.privernal.dynamic.advanced.mapper.DynamicShareReceiptMapper;
import com.privernal.dynamic.dba.utils.FormDataTitleUtils;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表单外链服务（§5）：填写/查看外链的配置管理与免登链路核心逻辑。
 * <p>
 * 安全实现（§5.3）：token 只存 SHA-256 摘要；停用/过期/超限一律 404 不区分原因；
 * IP 滑动窗口限流 + 指纹限填；密码 bcrypt；回执码只存摘要。
 * P0 限流/指纹/密码锁为进程内实现（单实例语义），Redis 化是 P1 项，接口签名不变。
 */
@Slf4j
@Service
public class FormShareService {

    /** IP 级滑动窗口：默认 30 次/分钟（§5.3-3） */
    private static final int IP_WINDOW_LIMIT = 30;
    private static final long IP_WINDOW_MS = 60_000L;
    private static final long IP_BAN_MS = 10 * 60_000L;
    private static final int PASSWORD_MAX_FAILS = 5;
    private static final long PASSWORD_LOCK_MS = 10 * 60_000L;
    /** 指纹限填的计数窗口（无外链有效期时用） */
    private static final long FINGERPRINT_WINDOW_MS = 30L * 24 * 3600 * 1000;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private DynamicFormShareMapper shareMapper;

    @Autowired
    private DynamicShareReceiptMapper receiptMapper;

    @Autowired
    private DynamicShareAccessLogMapper accessLogMapper;

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private LinkageSupport linkageSupport;

    @Autowired
    private com.privernal.dynamic.advanced.engine.AdvancedKillSwitch killSwitch;

    /**
     * 密文能力按接口注入（privernal-system-api 下沉），单体内由
     * system-core 的 MachineSecretCipher 提供实现。required=false：单独部署 dynamic
     * 的极端场景下回显能力显式报错，不降级明文。
     */
    @Autowired(required = false)
    private com.privernal.system.api.ISecretCipherService secretCipherService;

    @Autowired
    private com.privernal.dynamic.dbs.support.FailClosedConfig failClosedConfig;

    /** IP → 窗口起点时间戳（滑动窗口简化为固定窗口，窗口 1 分钟） */
    private final Map<String, long[]> ipWindows = new ConcurrentHashMap<>();
    /** IP → 封禁截止 */
    private final Map<String, Long> ipBannedUntil = new ConcurrentHashMap<>();
    /** IP → 密码失败计数与锁定截止 */
    private final Map<String, long[]> passwordFails = new ConcurrentHashMap<>();
    /** shareId|指纹 → [次数, 窗口起点] */
    private final Map<String, long[]> fingerprintCounters = new ConcurrentHashMap<>();

    // ---------------- 管理端配置 ----------------

    /**
     * 创建外链。token 明文只在本次返回（§5.3-1），库里只存摘要。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createShare(DynamicFormShare share, DynamicForm form) {
        if (share.getShareType() == null || (!DynamicFormShare.TYPE_FILL.equals(share.getShareType())
                && !DynamicFormShare.TYPE_VIEW.equals(share.getShareType()))) {
            throw new ServiceException("share_type 必须为 FILL/VIEW");
        }
        if (DynamicFormShare.TYPE_FILL.equals(share.getShareType())) {
            // 填写链固定匿名公开（§5.4）；查看链才区分三种 scope
            share.setScopeType(DynamicFormShare.SCOPE_ANON_PUBLIC);
        } else if (StringUtils.isEmpty(share.getScopeType())) {
            share.setScopeType(DynamicFormShare.SCOPE_ANON_PUBLIC);
        }
        if (share.getPerPersonLimit() == null || share.getPerPersonLimit() < 1) {
            share.setPerPersonLimit(1);
        }
        String token = LinkageIdemUtils.randomToken();
        share.setTokenDigest(LinkageIdemUtils.sha256Hex(token));
        share.setTokenTail(token.substring(token.length() - 4));
        share.setEnabled("Y");
        share.setSubmitCount(0);
        // 密码门：创建时即 bcrypt 落库（此前从不 encode，密码门形同虚设）
        share.setPassword(encodePassword(share.getPassword()));
        // 链接回显：token 明文加密落 link_cipher（密钥复用 openapi.secret-master-key）。
        // 密钥未配置时留空 = 存量旧行同语义，取链报错并提示 rotate，绝不降级明文（fail-closed）
        share.setLinkCipher(encryptToken(token));
        if (StringUtils.isEmpty(share.getOrganizationId())) {
            share.setOrganizationId(form.getOrganizationId());
        }
        if (StringUtils.isEmpty(share.getDepartmentId())) {
            share.setDepartmentId(form.getDepartmentId());
        }
        if (StringUtils.isEmpty(share.getProxyUserId())) {
            // 代理用户默认取表单创建人（§5.1）：动态表 created_by NOT NULL，匿名提交必须有归属
            share.setProxyUserId(form.getCreatedBy());
        }
        shareMapper.insert(share);
        Map<String, Object> result = new HashMap<>();
        result.put("id", share.getId());
        result.put("token", token);
        result.put("tokenTail", share.getTokenTail());
        return result;
    }

    /** 轮换 token：旧摘要立即失效（§5.3-1），新 token 同步重加密落 link_cipher */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rotateToken(String id) {
        DynamicFormShare share = requireShare(id);
        String token = LinkageIdemUtils.randomToken();
        DynamicFormShare update = new DynamicFormShare();
        update.setId(id);
        update.setTokenDigest(LinkageIdemUtils.sha256Hex(token));
        update.setTokenTail(token.substring(token.length() - 4));
        update.setLinkCipher(encryptToken(token));
        shareMapper.updateById(update);
        return Map.of("id", id, "token", token, "tokenTail", update.getTokenTail());
    }

    /**
     * 配置可改：不动 token、不动 link_cipher、tokenTail。
     * 可变集 = expireTime/submitLimit/perPersonLimit/shareType/scopeType/scopeDeptIds/
     * captcha/editableAfterSubmit/proxyUserId/enabled（整表替换语义，前端整单提交）。
     * password：非空重新 encode，空/缺省清空该列——防止改一次配置把摘要覆盖成明文
     * （同坑二次复发防线）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DynamicFormShare updateShare(String id, DynamicFormShare patch) {
        DynamicFormShare existing = requireShare(id);
        if (patch.getShareType() == null || (!DynamicFormShare.TYPE_FILL.equals(patch.getShareType())
                && !DynamicFormShare.TYPE_VIEW.equals(patch.getShareType()))) {
            throw new ServiceException("share_type 必须为 FILL/VIEW");
        }
        if (DynamicFormShare.TYPE_FILL.equals(patch.getShareType())) {
            patch.setScopeType(DynamicFormShare.SCOPE_ANON_PUBLIC);
        } else if (StringUtils.isEmpty(patch.getScopeType())) {
            patch.setScopeType(DynamicFormShare.SCOPE_ANON_PUBLIC);
        }
        if (patch.getPerPersonLimit() == null || patch.getPerPersonLimit() < 1) {
            patch.setPerPersonLimit(1);
        }
        DynamicFormShare update = new DynamicFormShare();
        update.setId(id);
        // captcha/editable_after_submit 列 NOT NULL DEFAULT 'N'：显式 set 时 null 必须规整为 'N'
        update.setShareType(patch.getShareType());
        update.setScopeType(patch.getScopeType());
        update.setScopeDeptIds(patch.getScopeDeptIds());
        update.setExpireTime(patch.getExpireTime());
        update.setSubmitLimit(patch.getSubmitLimit());
        update.setPerPersonLimit(patch.getPerPersonLimit());
        update.setCaptcha(StringUtils.isEmpty(patch.getCaptcha()) ? "N" : patch.getCaptcha());
        update.setEditableAfterSubmit(StringUtils.isEmpty(patch.getEditableAfterSubmit())
                ? "N" : patch.getEditableAfterSubmit());
        update.setProxyUserId(StringUtils.isEmpty(patch.getProxyUserId())
                ? existing.getProxyUserId() : patch.getProxyUserId());
        update.setPrefillFields(patch.getPrefillFields());
        update.setEnabled(patch.getEnabled() == null ? existing.getEnabled()
                : ("Y".equals(patch.getEnabled()) ? "Y" : "N"));
        // 密码门：空 = 显式清除密码门；非空 = 重新 encode（服务端统一 encode，前端传明文无需改）
        update.setPassword(encodePassword(patch.getPassword()));
        shareMapper.update(null, Wrappers.<DynamicFormShare>lambdaUpdate()
                .eq(DynamicFormShare::getId, id)
                .set(DynamicFormShare::getShareType, update.getShareType())
                .set(DynamicFormShare::getScopeType, update.getScopeType())
                .set(DynamicFormShare::getScopeDeptIds, update.getScopeDeptIds())
                .set(DynamicFormShare::getExpireTime, update.getExpireTime())
                .set(DynamicFormShare::getSubmitLimit, update.getSubmitLimit())
                .set(DynamicFormShare::getPerPersonLimit, update.getPerPersonLimit())
                .set(DynamicFormShare::getCaptcha, update.getCaptcha())
                .set(DynamicFormShare::getEditableAfterSubmit, update.getEditableAfterSubmit())
                .set(DynamicFormShare::getProxyUserId, update.getProxyUserId())
                .set(DynamicFormShare::getPrefillFields, update.getPrefillFields())
                .set(DynamicFormShare::getEnabled, update.getEnabled())
                .set(DynamicFormShare::getPassword, update.getPassword()));
        return requireShare(id);
    }

    /**
     * 链接回显：解密 link_cipher 返回完整 URL（开关 fail-closed 默认关）。
     * 存量旧行（无 link_cipher）→ 报错提示 rotate（不迁移、不做兼容 UI）；
     * 密钥缺失/解密失败 → 直接报错，绝不降级返回明文或摘要。
     * 每次取链写审计（dynamic_share_access_log，action=REVEAL，含操作人）。
     */
    public Map<String, Object> revealLink(String id, String operatorId, String clientIp, String userAgent) {
        if (!shareEnabled()) {
            throw new ServiceException("外链能力已被平台关停（kill switch）");
        }
        if (!failClosedConfig.shareRevealEnabled()) {
            throw new ServiceException("外链回显未开启（dynamic.share.reveal-enabled），请联系管理员显式开启");
        }
        DynamicFormShare share = requireShare(id);
        if (StringUtils.isEmpty(share.getLinkCipher())) {
            throw new ServiceException("该外链创建于回显能力上线前或主密钥未配置，无法回显；请 rotate 后使用新链接");
        }
        if (secretCipherService == null || !secretCipherService.isConfigured()) {
            throw new ServiceException("未配置 openapi.secret-master-key，无法解密链接");
        }
        String token;
        try {
            token = secretCipherService.decrypt(share.getLinkCipher());
        } catch (Exception e) {
            log.warn("外链取链解密失败 id={} error={}", id, e.getMessage());
            throw new ServiceException("链接解密失败（主密钥可能已变更）；请 rotate 重建外链");
        }
        if (StringUtils.isEmpty(token) || !LinkageIdemUtils.sha256Hex(token).equals(share.getTokenDigest())) {
            // 密文与摘要不一致 = 密钥换过或数据被手改，按失效处理（不返回任何可用内容）
            throw new ServiceException("链接密文与摘要不一致，链接已失效；请 rotate 重建外链");
        }
        String origin = failClosedConfig.sharePublicOrigin();
        if (StringUtils.isEmpty(origin)) {
            throw new ServiceException("未配置 dynamic.share.public-origin，无法生成完整链接");
        }
        String path = DynamicFormShare.TYPE_VIEW.equals(share.getShareType())
                ? "/pub/view/" + token : "/pub/form/" + token;
        logAccess(share, DynamicShareAccessLog.ACTION_REVEAL,
                DynamicShareAccessLog.RESULT_SUCCESS, null, clientIp, userAgent, null, operatorId);
        Map<String, Object> result = new HashMap<>();
        result.put("url", origin + path);
        result.put("tokenTail", share.getTokenTail());
        result.put("shareType", share.getShareType());
        result.put("expireTime", share.getExpireTime());
        result.put("enabled", share.getEnabled());
        return result;
    }

    /** 密码统一 encode 口径：空/空白 → null（清空密码门）；非空 → bcrypt */
    private String encodePassword(String rawPassword) {
        if (StringUtils.isEmpty(rawPassword)) {
            return null;
        }
        return passwordEncoder.encode(rawPassword);
    }

    private String encryptToken(String token) {
        if (secretCipherService == null || !secretCipherService.isConfigured()) {
            // 密钥未配置：link_cipher 留空，与存量旧行同语义（取链报错提示 rotate），不降级明文
            return null;
        }
        return secretCipherService.encrypt(token);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String enabled) {
        requireShare(id);
        DynamicFormShare update = new DynamicFormShare();
        update.setId(id);
        update.setEnabled("Y".equals(enabled) ? "Y" : "N");
        shareMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteShare(String id) {
        requireShare(id);
        shareMapper.deleteById(id);
    }

    public List<DynamicFormShare> listByFormId(String formId) {
        return shareMapper.selectList(Wrappers.<DynamicFormShare>lambdaQuery()
                .eq(DynamicFormShare::getFormId, formId)
                .orderByDesc(DynamicFormShare::getCreatedTime));
    }

    // ---------------- 免登链路 ----------------

    /** token 解析结果：share + form + 业务库（§5.5 master 定位链） */
    public record ShareResolution(DynamicFormShare share, DynamicForm form, String database) {
    }

    /**
     * 免登入口的解析链（§5.5）：master 按 token 摘要 → share → form → 组织 + 数据库，
     * 并把组织上下文注入调用方（调用方负责 finally 恢复）。
     * 启用/有效期任一不过返回 404 语义异常（不区分原因，防探测，§5.3-4）。
     */
    public ShareResolution resolveByToken(String token, String expectType) {
        if (StringUtils.isEmpty(token) || token.length() != 32 || !token.chars().allMatch(c ->
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            throw notFound();
        }
        DynamicFormShare share = shareMapper.selectOne(Wrappers.<DynamicFormShare>lambdaQuery()
                .eq(DynamicFormShare::getTokenDigest, LinkageIdemUtils.sha256Hex(token)));
        if (share == null || !"Y".equals(share.getEnabled())) {
            throw notFound();
        }
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            throw notFound();
        }
        if (expectType != null && !expectType.equals(share.getShareType())) {
            throw notFound();
        }
        DynamicForm form = linkageSupport.loadForm(share.getFormId());
        if (form == null || StringUtils.isEmpty(form.getTableCode())) {
            throw notFound();
        }
        String database = linkageSupport.resolveDatabase(share.getOrganizationId());
        OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
        OrganizationContextHolder.setOrganizationId(share.getOrganizationId());
        return new ShareResolution(share, form, database);
    }

    public void checkIpRateLimit(String clientIp) {
        if (StringUtils.isEmpty(clientIp)) {
            return;
        }
        Long bannedUntil = ipBannedUntil.get(clientIp);
        if (bannedUntil != null && bannedUntil > System.currentTimeMillis()) {
            throw new ServiceException("访问过于频繁，请稍后再试");
        }
        long[] window = ipWindows.computeIfAbsent(clientIp, k -> new long[2]);
        long now = System.currentTimeMillis();
        synchronized (window) {
            if (now - window[0] > IP_WINDOW_MS) {
                window[0] = now;
                window[1] = 0;
            }
            window[1]++;
            if (window[1] > IP_WINDOW_LIMIT) {
                ipBannedUntil.put(clientIp, now + IP_BAN_MS);
                throw new ServiceException("访问过于频繁，请稍后再试");
            }
        }
    }

    public void verifyAccessPassword(DynamicFormShare share, String inputPassword, String clientIp) {
        if (StringUtils.isEmpty(share.getPassword())) {
            return;
        }
        if (StringUtils.isEmpty(inputPassword)) {
            throw new ServiceException("需要访问密码");
        }
        String key = clientIp == null ? "anonymous" : clientIp;
        long[] state = passwordFails.computeIfAbsent(key, k -> new long[2]);
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (state[1] > now) {
                throw new ServiceException("密码错误次数过多，已临时锁定");
            }
            if (!passwordEncoder.matches(inputPassword, share.getPassword())) {
                state[0]++;
                if (state[0] >= PASSWORD_MAX_FAILS) {
                    state[1] = now + PASSWORD_LOCK_MS;
                    state[0] = 0;
                    throw new ServiceException("密码错误次数过多，已临时锁定");
                }
                throw new ServiceException("访问密码错误");
            }
            state[0] = 0;
        }
    }

    /**
     * 匿名指纹限填（§5.1 每人限填）：IP+UA 指纹计数，明示"匿名场景仅尽力防重"
     */
    public boolean isFingerprintOverLimit(DynamicFormShare share, String fingerprint) {
        return isFingerprintOverLimit(share, fingerprint, "sub");
    }

    /**
     * 带命名空间的指纹计数：提交(sub)与回执修改(upd)分开计数——提交把 sub 槽位用尽后，
     * 修改同一条数据不应被提交名额误伤（§5.3-7 的"计入限流"指修改入口自身也要有频控）。
     */
    public boolean isFingerprintOverLimit(DynamicFormShare share, String fingerprint, String scope) {
        int limit = "upd".equals(scope)
                ? Math.max(share.getPerPersonLimit() == null ? 1 : share.getPerPersonLimit(), 5)
                : (share.getPerPersonLimit() == null ? 1 : share.getPerPersonLimit());
        String key = share.getId() + "|" + scope + "|" + fingerprint;
        long[] counter = fingerprintCounters.computeIfAbsent(key, k -> new long[2]);
        long now = System.currentTimeMillis();
        synchronized (counter) {
            if (now - counter[1] > FINGERPRINT_WINDOW_MS) {
                counter[0] = 0;
                counter[1] = now;
            }
            return counter[0] >= limit;
        }
    }

    public void recordFingerprint(DynamicFormShare share, String fingerprint) {
        recordFingerprint(share, fingerprint, "sub");
    }

    public void recordFingerprint(DynamicFormShare share, String fingerprint, String scope) {
        String key = share.getId() + "|" + scope + "|" + fingerprint;
        long[] counter = fingerprintCounters.computeIfAbsent(key, k -> new long[2]);
        synchronized (counter) {
            counter[0]++;
            if (counter[1] == 0) {
                counter[1] = System.currentTimeMillis();
            }
        }
    }

    /** 原子消费提交名额（停用/过期/超限返回 false → 上层 404） */
    public boolean consumeSubmitQuota(String shareId) {
        return shareMapper.consumeSubmitQuota(shareId) > 0;
    }

    public void releaseSubmitQuota(String shareId) {
        shareMapper.releaseSubmitQuota(shareId);
    }

    /** 回执码签发：原文一次性返回，库存摘要（§5.3-7） */
    @Transactional(rollbackFor = Exception.class)
    public String issueReceipt(String shareId, String dataId) {
        String code = LinkageIdemUtils.randomReceiptCode();
        DynamicFormShare shareRow = shareMapper.selectById(shareId);
        DynamicShareReceipt receipt = new DynamicShareReceipt();
        receipt.setShareId(shareId);
        receipt.setReceiptDigest(LinkageIdemUtils.sha256Hex(code));
        receipt.setDataId(dataId);
        receipt.setOrganizationId(shareRow.getOrganizationId());
        receipt.setDepartmentId(shareRow.getDepartmentId());
        receiptMapper.insert(receipt);
        return code;
    }

    /** 回执码 → 数据行 ID（摘要定位，禁止可枚举 ID 作回执码） */
    public String findDataIdByReceipt(String shareId, String receiptCode) {
        if (StringUtils.isEmpty(receiptCode)) {
            throw notFound();
        }
        DynamicShareReceipt receipt = receiptMapper.selectOne(Wrappers.<DynamicShareReceipt>lambdaQuery()
                .eq(DynamicShareReceipt::getShareId, shareId)
                .eq(DynamicShareReceipt::getReceiptDigest, LinkageIdemUtils.sha256Hex(receiptCode.trim().toUpperCase())));
        return receipt == null ? null : receipt.getDataId();
    }

    public void logAccess(DynamicFormShare share, String action, String result, String denyReason,
                          String clientIp, String userAgent, String dataId) {
        logAccess(share, action, result, denyReason, clientIp, userAgent, dataId, null);
    }

    /** 带操作人的审计入口（管理端动作如 REVEAL 取链） */
    public void logAccess(DynamicFormShare share, String action, String result, String denyReason,
                          String clientIp, String userAgent, String dataId, String operatorId) {
        try {
            DynamicShareAccessLog row = new DynamicShareAccessLog();
            row.setOrganizationId(share == null ? "" : share.getOrganizationId());
            row.setShareId(share == null ? null : share.getId());
            row.setFormId(share == null ? null : share.getFormId());
            row.setAction(action);
            row.setTokenTail(share == null ? null : share.getTokenTail());
            row.setClientIp(clientIp);
            row.setUserAgent(userAgent == null ? null : StringUtils.substring(userAgent, 0, 490));
            row.setResult(result);
            row.setDenyReason(denyReason);
            row.setDataId(dataId);
            row.setOperatorId(operatorId);
            row.setCreatedTime(new Date());
            accessLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("外链访问日志落库失败 action={} error={}", action, e.getMessage());
        }
    }

    /**
     * 外链提交/修改时构造数据：剔除保留键、拼数据标题、唯一性校验、流程表单落 0（§5.1）
     */
    public void prepareSubmitData(DynamicForm form, Map<String, Object> data, boolean isInsert) {
        data.keySet().removeIf(k -> k != null && (k.startsWith("__") || "tableCode".equals(k)
                || "startFlow".equals(k) || "metaData".equals(k)));
        JSONObject metaData = form.getMetaData();
        if (metaData != null) {
            String dataTitle = FormDataTitleUtils.buildTitle(metaData, data);
            if (StringUtils.isNotEmpty(dataTitle)) {
                data.put("title", dataTitle);
            }
        }
        if (isInsert && form.getIsWorkflow() != null && form.getIsWorkflow() == 1
                && StringUtils.isNotEmpty(form.getWorkflowCode())) {
            // 工作流表单外链提交固定落 0 草稿，不自动发起（§5.1，与 CommonController 同构）
            data.put("status", 0);
        }
    }

    /**
     * 免登提交核心：走服务层收敛点，sourceTag=PUBLIC_LINK + 代理身份（§5.5），
     * 不复用 CommonController（那里取会话数据源，免登无登录态）
     */
    public String submitData(DynamicFormShare share, DynamicForm form, String database,
                             Map<String, Object> data, String fingerprint) {
        boolean isInsert = data.get("id") == null;
        prepareSubmitData(form, data, isInsert);
        // 来源标记落 remark（§5.1）：列表按前缀渲染"外链"角标；token 尾号+指纹前 8 位可审计
        if (isInsert && StringUtils.isEmpty((String) data.get("remark"))) {
            data.put("remark", "PUBLIC_LINK:" + share.getTokenTail() + ":"
                    + StringUtils.substring(fingerprint == null ? "" : fingerprint, 0, 8));
        }
        String proxyUser = StringUtils.isEmpty(share.getProxyUserId()) ? form.getCreatedBy() : share.getProxyUserId();
        DataWriteAttributes.Snapshot previous = DataWriteAttributes.push(
                DataWriteAttributes.SOURCE_PUBLIC_LINK, 0, proxyUser, form.getDepartmentId(), share.getOrganizationId());
        try {
            return dynamicTableService.saveOrUpdate(database, form.getTableCode(), data);
        } finally {
            DataWriteAttributes.restore(previous);
        }
    }

    private DynamicFormShare requireShare(String id) {
        DynamicFormShare share = shareMapper.selectById(id);
        if (share == null) {
            throw new ServiceException("外链不存在：" + id);
        }
        return share;
    }

    private ServiceException notFound() {
        // 停用/过期/超限/不存在统一 404 语义，不区分原因（§5.3-4）
        return new ServiceException("链接不存在或已失效");
    }

    /** 外链 kill switch（T9/D7，dynamic.advanced.share.enabled） */
    public boolean shareEnabled() {
        return killSwitch.shareEnabled();
    }
}
