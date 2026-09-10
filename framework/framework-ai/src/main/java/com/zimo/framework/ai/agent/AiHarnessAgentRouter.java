package com.zimo.framework.ai.agent;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 按显式配置、渠道绑定、渠道默认和全局默认顺序解析智能体。
 *
 * <p>路由结果必须属于请求租户、处于启用状态且包含有效智能体标识；不合格候选会继续降级到下一层。</p>
 *
 * @author Codex
 * @since 2026-07-25
 */
public class AiHarnessAgentRouter {

    private final AiAgentProfileResolver profileResolver;
    private final Function<AiAgentRouteRequest, AiAgentProfile> globalDefaultProfileProvider;
    /** 运行时 Profile 补丁列表（对应 dsh patch 覆盖层）。 */
    private final List<AiProfilePatchProvider> patches;

    /**
     * 创建使用固定全局默认配置的分层路由器。
     *
     * @param profileResolver 渠道智能体配置解析器，允许为空
     * @param globalDefaultProfile 全局默认智能体配置，允许为空，但必须带有匹配租户才能命中
     */
    public AiHarnessAgentRouter(
            AiAgentProfileResolver profileResolver,
            AiAgentProfile globalDefaultProfile) {
        this(profileResolver, request -> globalDefaultProfile);
    }

    /**
     * 创建使用请求级全局默认配置的分层路由器。
     *
     * @param profileResolver 渠道智能体配置解析器，允许为空
     * @param globalDefaultProfileProvider 根据请求租户创建默认配置的函数，不允许为空
     * @return 支持多租户全局默认配置的分层路由器
     */
    public static AiHarnessAgentRouter withDefaultProvider(
            AiAgentProfileResolver profileResolver,
            Function<AiAgentRouteRequest, AiAgentProfile> globalDefaultProfileProvider) {
        return new AiHarnessAgentRouter(
                profileResolver,
                Objects.requireNonNull(
                        globalDefaultProfileProvider,
                        "globalDefaultProfileProvider must not be null"));
    }

    private AiHarnessAgentRouter(
            AiAgentProfileResolver profileResolver,
            Function<AiAgentRouteRequest, AiAgentProfile> globalDefaultProfileProvider) {
        this(profileResolver, globalDefaultProfileProvider, List.of());
    }

    /**
     * @param patches 运行时 Profile 补丁（可空）
     */
    public AiHarnessAgentRouter(
            AiAgentProfileResolver profileResolver,
            Function<AiAgentRouteRequest, AiAgentProfile> globalDefaultProfileProvider,
            List<AiProfilePatchProvider> patches) {
        this.profileResolver = profileResolver;
        this.globalDefaultProfileProvider = globalDefaultProfileProvider;
        this.patches = patches == null ? List.of() : patches;
    }

    /**
     * 按固定优先级解析可用智能体配置。
     *
     * @param request 路由请求；为空时直接返回空结果
     * @return 首个满足启用、标识有效且租户一致约束的配置；无可用配置时返回空
     */
    /** 应用运行时补丁：原始 profile 逐个经过 patch 提供方改写；空输入返回空。 */
    private Optional<AiAgentProfile> applyPatches(Optional<AiAgentProfile> original, AiAgentRouteRequest request) {
        if (original.isEmpty()) {
            return original;
        }
        AiAgentProfile current = original.get();
        for (AiProfilePatchProvider patch : patches) {
            Optional<AiAgentProfile> patched = patch.patch(current, request);
            if (patched.isPresent()) {
                current = patched.get();
            }
        }
        return Optional.of(current);
    }

    public Optional<AiAgentProfile> route(AiAgentRouteRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        Optional<AiAgentProfile> profile = usable(request, request.explicitProfile());
        if (profile.isPresent()) {
            return applyPatches(profile, request);
        }

        profile = resolveChannelBinding(request);
        if (profile.isPresent()) {
            return applyPatches(profile, request);
        }

        profile = resolveChannelDefault(request);
        if (profile.isPresent()) {
            return applyPatches(profile, request);
        }
        return applyPatches(usable(request, globalDefaultProfileProvider.apply(request)), request);
    }

    private Optional<AiAgentProfile> resolveChannelBinding(AiAgentRouteRequest request) {
        if (profileResolver == null || request.channelMessage() == null) {
            return Optional.empty();
        }
        return usable(
                request,
                profileResolver.resolveForMessage(request.channelMessage()).orElse(null));
    }

    private Optional<AiAgentProfile> resolveChannelDefault(AiAgentRouteRequest request) {
        if (profileResolver == null || request.channel() == null) {
            return Optional.empty();
        }
        return usable(
                request,
                profileResolver.resolveDefaultForChannel(
                        request.channel(),
                        request.tenantId()).orElse(null));
    }

    private Optional<AiAgentProfile> usable(
            AiAgentRouteRequest request,
            AiAgentProfile profile) {
        if (profile == null
                || !profile.enabled()
                || profile.id() == null
                || profile.id().isBlank()
                || profile.tenantId() == null
                || !profile.tenantId().equals(request.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(profile);
    }
}
