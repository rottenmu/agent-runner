package com.zimo.module.security.service;

import com.zimo.module.security.entity.SecDangerousApi;
import com.zimo.module.security.entity.SecSensitiveWord;
import com.zimo.module.security.mapper.SecDangerousApiMapper;
import com.zimo.module.security.mapper.SecSensitiveWordMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.auth.security.AuthSessionService;
import com.zimo.module.auth.support.CurrentLoginUserUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内容安全管控服务：敏感词拦截、数据脱敏、隐私屏蔽、高危接口拦截、幻觉风险标记。
 *
 * <p>敏感词分三级处置：block（拦截）/ mask（掩码替换）/ alert（仅告警）。
 * 脱敏复用内存敏感过滤器规则（手机号、身份证、银行卡、邮箱、API 密钥等）。
 * 幻觉标记：RAG 回答声明引用了知识库但无对应来源片段时标记风险。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ContentSafetyService {

    /** 敏感词处置级别：拦截（输入阻断）。 */
    public static final String CATEGORY_BLOCK = "block";
    /** 敏感词处置级别：掩码（输出替换为星号）。 */
    public static final String CATEGORY_MASK = "mask";
    /** 敏感词处置级别：仅告警。 */
    public static final String CATEGORY_ALERT = "alert";

    private final SecSensitiveWordMapper wordMapper;
    private final SecDangerousApiMapper apiMapper;
    private final com.zimo.module.agentmemory.security.AiMemorySensitiveFilter piiFilter;
    private final AuthSessionService sessionService;

    public ContentSafetyService(SecSensitiveWordMapper wordMapper,
                                SecDangerousApiMapper apiMapper,
                                AuthSessionService sessionService) {
        this.wordMapper = wordMapper;
        this.apiMapper = apiMapper;
        this.sessionService = sessionService;
        this.piiFilter = new com.zimo.module.agentmemory.security.AiMemorySensitiveFilter();
        initSeeds();
    }

    /* ---------------- 敏感词 ---------------- */

    /** 敏感词列表。 */
    public List<SecSensitiveWord> listWords() {
        return wordMapper.selectList(Wrappers.<SecSensitiveWord>lambdaQuery()
                .orderByDesc(SecSensitiveWord::getId));
    }

    /** 新增敏感词。 */
    public SecSensitiveWord addWord(String word, String category) {
        SecSensitiveWord existing = wordMapper.selectOne(Wrappers.<SecSensitiveWord>lambdaQuery()
                .eq(SecSensitiveWord::getWord, word));
        if (existing != null) {
            existing.setCategory(category == null ? CATEGORY_BLOCK : category);
            wordMapper.updateById(existing);
            return existing;
        }
        SecSensitiveWord entity = new SecSensitiveWord();
        entity.setWord(word);
        entity.setCategory(category == null ? CATEGORY_BLOCK : category);
        entity.setCreatedAt(LocalDateTime.now());
        wordMapper.insert(entity);
        return entity;
    }

    /** 删除敏感词。 */
    public void deleteWord(Long id) {
        wordMapper.deleteById(id);
    }

    /**
     * 输入拦截检查：命中 block 类敏感词返回拦截原因。
     *
     * @return null=放行；否则拦截原因
     */
    public String interceptInput(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        List<SecSensitiveWord> words = wordMapper.selectList(
                Wrappers.<SecSensitiveWord>lambdaQuery().eq(SecSensitiveWord::getCategory, CATEGORY_BLOCK));
        for (SecSensitiveWord word : words) {
            if (text.contains(word.getWord())) {
                return "输入含违规内容「" + word.getWord() + "」已拦截";
            }
        }
        return null;
    }

    /**
     * 输出净化：mask 类敏感词替换 + PII 脱敏 + 隐私屏蔽。
     */
    public String sanitizeOutput(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        String result = text;
        // 1) mask 类敏感词 → 星号
        List<SecSensitiveWord> masks = wordMapper.selectList(
                Wrappers.<SecSensitiveWord>lambdaQuery().eq(SecSensitiveWord::getCategory, CATEGORY_MASK));
        for (SecSensitiveWord word : masks) {
            String w = word.getWord();
            if (!w.isBlank()) {
                result = result.replaceAll(Pattern.quote(w), "***");
            }
        }
        // 2) PII 脱敏（手机/身份证/银行卡/邮箱/密钥）
        result = piiFilter.sanitize(result);
        return result;
    }

    /**
     * 幻觉风险检测：回答声称引用知识库但实际无来源片段。
     *
     * @return null=无风险；否则风险说明
     */
    public String detectHallucination(String answer, int sourceCount) {
        if (StrUtil.isBlank(answer)) {
            return null;
        }
        boolean claimsCitation = answer.contains("[来源") || answer.contains("[1]")
                || answer.contains("[2]") || answer.contains("[3]")
                || answer.contains("根据知识库") || answer.contains("参考了知识库");
        if (claimsCitation && sourceCount == 0) {
            return "回答声称引用知识库但检索无来源片段，存在幻觉风险";
        }
        return null;
    }

    /* ---------------- 高危接口 ---------------- */

    /** 高危接口列表。 */
    public List<SecDangerousApi> listApis() {
        return apiMapper.selectList(Wrappers.<SecDangerousApi>lambdaQuery()
                .orderByDesc(SecDangerousApi::getId));
    }

    /** 新增高危接口规则。 */
    public SecDangerousApi addApi(String pattern, String method, String description) {
        SecDangerousApi api = new SecDangerousApi();
        api.setPattern(pattern);
        api.setMethod(StrUtil.isBlank(method) ? "*" : method);
        api.setDescription(description == null ? "" : description);
        api.setEnabled(1);
        api.setCreatedAt(LocalDateTime.now());
        apiMapper.insert(api);
        return api;
    }

    /** 删除高危接口规则。 */
    public void deleteApi(Long id) {
        apiMapper.deleteById(id);
    }

    /**
     * 高危接口检测（禁止 Agent 访问）。
     *
     * @return null=允许；否则拦截原因
     */
    public String checkDangerousUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        List<SecDangerousApi> apis = apiMapper.selectList(
                Wrappers.<SecDangerousApi>lambdaQuery().eq(SecDangerousApi::getEnabled, 1));
        for (SecDangerousApi api : apis) {
            Pattern pattern = Pattern.compile(api.getPattern());
            if (pattern.matcher(url).find()) {
                return "高危接口被禁止访问: " + url
                        + (api.getDescription().isBlank() ? "" : "（" + api.getDescription() + "）");
            }
        }
        return null;
    }

    /* ---------------- 审计辅助 ---------------- */

    /** 当前登录用户 ID（供审计）。 */
    public String currentUserId() {
        try {
            Long userId = CurrentLoginUserUtils.currentUserId(sessionService);
            return userId == null ? "" : String.valueOf(userId);
        } catch (Exception e) {
            return "";
        }
    }

    private void initSeeds() {
        try {
            if (wordMapper.selectCount(null) == 0) {
                addWord("赌博", CATEGORY_BLOCK);
                addWord("博彩", CATEGORY_BLOCK);
                addWord("洗钱", CATEGORY_BLOCK);
                addWord("毒品", CATEGORY_BLOCK);
                addWord("枪支", CATEGORY_BLOCK);
                addWord("裸聊", CATEGORY_BLOCK);
                addWord("发票代开", CATEGORY_MASK);
                addWord("私户收款", CATEGORY_MASK);
            }
            if (apiMapper.selectCount(null) == 0) {
                addApi("(/admin|/api/auth/login|/api/biz/ai/tools/governance/reset)", "*", "管理后台与认证接口禁止 Agent 访问");
                addApi("(/drop|/truncate|/delete|remove).*(table|users|permission)", "*", "危险数据操作接口");
                addApi("(localhost|127\\.0\\.0\\.1).*:(22|3306|5432|6379)", "*", "内部基础设施端口");
            }
        } catch (Exception ignored) {
            // 种子初始化失败不影响启动
        }
    }
}
