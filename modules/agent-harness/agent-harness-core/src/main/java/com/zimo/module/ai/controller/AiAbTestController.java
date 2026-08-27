package com.zimo.module.ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.management.AiAbTest;
import com.zimo.module.ai.management.AiAbTestService;
import com.zimo.module.ai.management.AiPromptVersion;
import com.zimo.module.ai.management.AiPromptVersionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提示词 AB 测试接口。
 *
 * <p>支持创建 / 启动 / 停止 / 选定赢家 / 删除，状态流转：draft → running → finished。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/ai/ab-tests")
public class AiAbTestController {

    private final AiAbTestService abTestService;
    private final AiPromptVersionService versionService;

    public AiAbTestController(AiAbTestService abTestService, AiPromptVersionService versionService) {
        this.abTestService = abTestService;
        this.versionService = versionService;
    }

    /** 查询全部 AB 测试（按创建时间倒序）。 */
    @GetMapping
    public ApiResponse<List<AiAbTest>> list() {
        return ApiResponse.ok(abTestService.list(Wrappers.<AiAbTest>lambdaQuery()
                .orderByDesc(AiAbTest::getCreateTime)));
    }

    /** 创建 AB 测试（草稿状态）。 */
    @PostMapping
    public ApiResponse<AiAbTest> create(@RequestBody AiAbTest test) {
        if (!StringUtils.hasText(test.getTestName())) {
            throw new IllegalArgumentException("测试名称不能为空");
        }
        if (test.getPromptId() == null || test.getVersionA() == null || test.getVersionB() == null) {
            throw new IllegalArgumentException("模板与两个对比版本均不能为空");
        }
        if (test.getVersionA().equals(test.getVersionB())) {
            throw new IllegalArgumentException("版本 A 与版本 B 不能相同");
        }
        requireVersion(test.getVersionA());
        requireVersion(test.getVersionB());
        test.setId(null);
        test.setStatus("draft");
        test.setWinner(null);
        test.setStartTime(null);
        test.setEndTime(null);
        test.setCreateTime(LocalDateTime.now());
        test.setUpdateTime(LocalDateTime.now());
        abTestService.save(test);
        return ApiResponse.ok(test);
    }

    /** 启动测试（草稿 → 运行中）。 */
    @PutMapping("/{id}/start")
    public ApiResponse<AiAbTest> start(@PathVariable Long id) {
        AiAbTest test = requireTest(id);
        if (!"draft".equals(test.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的测试可以启动");
        }
        test.setStatus("running");
        test.setStartTime(LocalDateTime.now());
        test.setUpdateTime(LocalDateTime.now());
        abTestService.updateById(test);
        return ApiResponse.ok(test);
    }

    /** 停止测试（运行中 → 已完成）。 */
    @PutMapping("/{id}/stop")
    public ApiResponse<AiAbTest> stop(@PathVariable Long id) {
        AiAbTest test = requireTest(id);
        if (!"running".equals(test.getStatus())) {
            throw new IllegalArgumentException("只有运行中的测试可以停止");
        }
        test.setStatus("finished");
        test.setEndTime(LocalDateTime.now());
        test.setUpdateTime(LocalDateTime.now());
        abTestService.updateById(test);
        return ApiResponse.ok(test);
    }

    /** 选定获胜版本。 */
    @PutMapping("/{id}/winner")
    public ApiResponse<AiAbTest> setWinner(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        AiAbTest test = requireTest(id);
        Long winner = body == null ? null : body.get("winner");
        ValidationUtil.requireNotNull(winner, "winner 不能为空");
        if (!winner.equals(test.getVersionA()) && !winner.equals(test.getVersionB())) {
            throw new IllegalArgumentException("获胜版本必须属于该测试的 A/B 版本");
        }
        test.setWinner(winner);
        if ("draft".equals(test.getStatus())) {
            test.setStatus("finished");
            test.setEndTime(LocalDateTime.now());
        }
        test.setUpdateTime(LocalDateTime.now());
        abTestService.updateById(test);
        return ApiResponse.ok(test);
    }

    /** 删除 AB 测试。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requireTest(id);
        abTestService.removeById(id);
        return ApiResponse.ok();
    }

    private AiAbTest requireTest(Long id) {
        AiAbTest test = abTestService.getById(id);
        ValidationUtil.requireNotNull(test, "AB 测试不存在: id=");
        return test;
    }

    private void requireVersion(Long versionId) {
        AiPromptVersion version = versionService.getById(versionId);
        ValidationUtil.requireNotNull(version, "版本不存在: id=");
    }
}
