package com.zimo.module.ai.skillimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.ZipInputStream;

/**
 * module-ai 单技能 ZIP 导入包解析器。
 *
 * <p>解析器先在内存中严格核对 ZIP 结构，再限制清单解压大小，并以严格 Jackson 配置读取根目录
 * {@code skill.json}。解析过程不写文件系统，也不会在异常中暴露清单内容。
 *
 * @author Codex
 * @since 2026-07-27
 */
public final class AiSkillZipParser {

    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final AiSkillZipStructureValidator STRUCTURE_VALIDATOR =
            new AiSkillZipStructureValidator();
    private final ObjectReader reader;

    /**
     * 使用全局 JSON 配置的副本创建严格导入解析器。
     *
     * @param objectMapper Spring Boot 提供的 JSON 映射器，不允许为空且不会被修改
     * @throws NullPointerException {@code objectMapper} 为空时抛出
     */
    public AiSkillZipParser(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        ObjectMapper strictMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        this.reader = strictMapper.readerFor(AiSkillImportManifest.class);
    }

    /**
     * 解析单技能 ZIP 并校验导入清单。
     *
     * @param archive ZIP 原始字节，必须包含唯一根目录普通文件 {@code skill.json}
     * @return 完成严格 JSON 解析和基础字段校验的导入清单
     * @throws AiSkillImportException ZIP 或清单格式错误时返回 {@code 400}，资源超限时返回 {@code 413}
     */
    public AiSkillImportManifest parse(byte[] archive) {
        validateHeader(archive);
        STRUCTURE_VALIDATOR.validate(archive);
        byte[] manifestBytes;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            manifestBytes = readValidatedArchive(input);
        } catch (AiSkillImportException exception) {
            throw exception;
        } catch (IOException exception) {
            throw AiSkillImportException.badRequest("ZIP 文件损坏或不受支持");
        }
        AiSkillImportManifest manifest = parseManifest(manifestBytes);
        validateManifest(manifest);
        return manifest;
    }

    private void validateHeader(byte[] archive) {
        if (archive == null || archive.length == 0) {
            throw AiSkillImportException.badRequest("ZIP 文件不能为空");
        }
        if (archive.length < 4
                || archive[0] != 0x50
                || archive[1] != 0x4B
                || archive[2] != 0x03
                || archive[3] != 0x04) {
            throw AiSkillImportException.badRequest("文件不是有效的 ZIP");
        }
    }

    private byte[] readValidatedArchive(ZipInputStream input) throws IOException {
        if (input.getNextEntry() == null) {
            throw AiSkillImportException.badRequest("ZIP 文件损坏或不受支持");
        }
        byte[] manifest = readManifest(input);
        input.closeEntry();
        if (input.getNextEntry() != null) {
            throw AiSkillImportException.badRequest("ZIP 文件损坏或不受支持");
        }
        return manifest;
    }

    private byte[] readManifest(ZipInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_MANIFEST_BYTES) {
                throw AiSkillImportException.tooLarge("skill.json 解压后不能超过 256 KB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private AiSkillImportManifest parseManifest(byte[] content) {
        try {
            return reader.readValue(content);
        } catch (JsonProcessingException exception) {
            throw AiSkillImportException.badRequest("skill.json 格式不合法");
        } catch (IOException exception) {
            throw AiSkillImportException.badRequest("skill.json 无法读取");
        }
    }

    private void validateManifest(AiSkillImportManifest manifest) {
        if (manifest.schemaVersion() != 1) {
            throw AiSkillImportException.badRequest("仅支持 schemaVersion 1");
        }
        requireText(manifest.name(), "技能名称不能为空");
        requireText(manifest.description(), "技能描述不能为空");
        if (manifest.apiConfig() == null) {
            throw AiSkillImportException.badRequest("apiConfig 不能为空");
        }
        requireText(manifest.apiConfig().baseUrl(), "apiConfig.baseUrl 不能为空");
        requireText(manifest.apiConfig().path(), "apiConfig.path 不能为空");
        validateHeaders(manifest.apiConfig());
        Integer timeoutMillis = manifest.apiConfig().timeoutMillis();
        if (timeoutMillis != null && timeoutMillis <= 0) {
            throw AiSkillImportException.badRequest("timeoutMillis 必须大于 0");
        }
    }

    private void validateHeaders(AiSkillImportManifest.ApiConfig apiConfig) {
        if (apiConfig.headers() == null) {
            return;
        }
        boolean hasNullEntry = apiConfig.headers().entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null);
        if (hasNullEntry) {
            throw AiSkillImportException.badRequest("apiConfig.headers 格式不合法");
        }
    }

    private void requireText(String value, String message) {
        if (StrUtil.isBlank(value)) {
            throw AiSkillImportException.badRequest(message);
        }
    }
}