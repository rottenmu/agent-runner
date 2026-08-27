package com.zimo.module.ai.skillimport;

import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedSkill;
import com.zimo.module.ai.management.AiManagedSkillRequest;
import com.zimo.module.ai.management.AiSkillApiConfigRequest;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import org.springframework.web.multipart.MultipartFile;

/**
 * module-ai 单技能 ZIP 导入编排服务。
 *
 * <p>服务校验上传文件、解析跨环境清单并转换为现有 API 技能创建请求，不建立新的事务边界。
 * 名称冲突、持久化和运行时注册异常均由 {@link AiAgentManagementService} 原样抛出。
 *
 * @author Codex
 * @since 2026-07-27
 */
public class AiSkillZipImportService {

    /** 上传 ZIP 的最大压缩字节数。 */
    private static final long MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L;

    private final AiSkillZipParser parser;
    private final AiAgentManagementService managementService;

    /**
     * 创建技能 ZIP 导入服务。
     *
     * @param parser 严格 ZIP 清单解析器，不允许为空
     * @param managementService 现有技能管理服务，不允许为空
     * @throws NullPointerException 任一依赖为空时抛出
     */
    public AiSkillZipImportService(
            AiSkillZipParser parser,
            AiAgentManagementService managementService) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.managementService = Objects.requireNonNull(
                managementService, "managementService must not be null");
    }

    /**
     * 导入单技能 ZIP 并通过现有创建流程注册和持久化 API 技能。
     *
     * <p>上传文件必须非空、扩展名为 {@code .zip} 且压缩大小不超过 2 MB。清单解析完成前
     * 不调用创建方法，转换后的请求不会携带智能体、提示词模板或 API 注册表绑定。
     *
     * @param file multipart 上传文件，不允许为空
     * @return 现有创建流程返回的技能视图
     * @throws AiSkillImportException 文件格式、读取或资源限制校验失败时抛出
     * @throws IllegalArgumentException 技能重名或现有创建参数校验失败时原样抛出
     * @throws RuntimeException 持久化或技能注册表故障时原样抛出
     */
    public AiManagedSkill importSkill(MultipartFile file) {
        validateUpload(file);
        AiSkillImportManifest manifest = parser.parse(readArchive(file));
        return managementService.createApiSkill(toCreateRequest(manifest));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AiSkillImportException.badRequest("请选择 ZIP 技能包");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw AiSkillImportException.badRequest("仅支持 .zip 技能包");
        }
        if (file.getSize() > MAX_ARCHIVE_BYTES) {
            throw AiSkillImportException.tooLarge("ZIP 技能包不能超过 2 MB");
        }
    }

    private byte[] readArchive(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw AiSkillImportException.badRequest("ZIP 技能包无法读取");
        }
    }

    private AiManagedSkillRequest toCreateRequest(AiSkillImportManifest manifest) {
        AiManagedSkillRequest request = new AiManagedSkillRequest();
        request.setName(manifest.name().trim());
        request.setDescription(manifest.description().trim());
        request.setReadOnly(manifest.readOnlyOrDefault());
        request.setAgentId(null);
        request.setPromptTemplateId(null);
        request.setApiConfig(toApiConfigRequest(manifest.apiConfig()));
        return request;
    }

    private AiSkillApiConfigRequest toApiConfigRequest(AiSkillImportManifest.ApiConfig manifest) {
        AiSkillApiConfigRequest request = new AiSkillApiConfigRequest();
        request.setApiRegistryId(null);
        request.setEnabled(manifest.enabledOrDefault());
        request.setBaseUrl(manifest.baseUrl().trim());
        request.setPath(manifest.path().trim());
        request.setMethod(manifest.methodOrDefault());
        request.setHeaders(manifest.headersOrDefault());
        request.setTimeoutMillis(manifest.timeoutMillisOrDefault());
        return request;
    }
}
