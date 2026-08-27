package com.zimo.module.ai.skillimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiSkillZipParserTest {

    private AiSkillZipParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiSkillZipParser(new ObjectMapper());
    }

    @Test
    void 解析唯一根目录清单并应用默认值() {
        byte[] archive = zip(entry("skill.json", """
                {
                  "schemaVersion": 1,
                  "name": "quality_query",
                  "description": "质量查询",
                  "apiConfig": {
                    "baseUrl": "https://api.example.com",
                    "path": "/quality"
                  }
                }
                """));

        AiSkillImportManifest manifest = parser.parse(archive);

        assertThat(manifest.name()).isEqualTo("quality_query");
        assertThat(manifest.readOnlyOrDefault()).isTrue();
        assertThat(manifest.apiConfig().enabledOrDefault()).isTrue();
        assertThat(manifest.apiConfig().methodOrDefault()).isEqualTo("POST");
        assertThat(manifest.apiConfig().timeoutMillisOrDefault()).isEqualTo(3000);
        assertThat(manifest.apiConfig().headersOrDefault()).isEmpty();
    }

    @Test
    void 拒绝额外目录或文件() {
        byte[] archive = zip(
                entry("skill.json", validManifest()),
                entry("README.md", "not allowed"));

        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 只能包含根目录 skill.json")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 拒绝空字节和错误ZIP文件头() {
        assertBadRequest(new byte[0]);
        assertBadRequest("not-a-zip".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 拒绝缺少skillJson() {
        byte[] archive = zip(entry("manifest.json", validManifest()));

        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 只能包含根目录 skill.json")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 拒绝嵌套和包含点点的条目路径() {
        assertInvalidEntry(zip(entry("nested/skill.json", validManifest())));
        assertInvalidEntry(zip(entry("../skill.json", validManifest())));
    }

    @Test
    void 拒绝重复skillJson() {
        byte[] archive = duplicateManifestArchive();

        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 只能包含根目录 skill.json")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 拒绝损坏或加密ZIP() {
        byte[] archive = zip(entry("skill.json", validManifest()));
        archive[8] = 99;
        archive[9] = 0;

        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 文件损坏或不受支持")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 拒绝截断的EOCD() {
        byte[] archive = zip(entry("skill.json", validManifest()));

        assertBadRequest(Arrays.copyOf(archive, archive.length - 1));
    }

    @Test
    void 拒绝缺失的中央目录() {
        byte[] archive = zip(entry("skill.json", validManifest()));
        int centralDirectoryOffset = findSignature(archive, 0x02014B50);
        archive[centralDirectoryOffset] = 0;

        assertBadRequest(archive);
    }

    @Test
    void 拒绝中央目录与本地条目名称不一致() {
        byte[] archive = zip(entry("skill.json", validManifest()));
        int centralDirectoryOffset = findSignature(archive, 0x02014B50);
        byte[] differentName = "spill.json".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(differentName, 0, archive, centralDirectoryOffset + 46, differentName.length);

        assertBadRequest(archive);
    }
    @Test
    void 拒绝未知schemaVersion() {
        byte[] archive = zip(entry("skill.json",
                validManifest().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));

        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("仅支持 schemaVersion 1")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 拒绝未知顶层字段() {
        byte[] archive = zip(entry("skill.json",
                validManifest().replace("\"schemaVersion\": 1", "\"unknown\": true,\n  \"schemaVersion\": 1")));

        assertInvalidManifest(archive);
    }

    @Test
    void 拒绝apiConfig中的apiRegistryId() {
        byte[] archive = zip(entry("skill.json",
                validManifest().replace("\"baseUrl\"", "\"apiRegistryId\": 9,\n    \"baseUrl\"")));

        assertInvalidManifest(archive);
    }

    @Test
    void 拒绝错误字段类型() {
        byte[] archive = zip(entry("skill.json",
                validManifest().replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")));

        assertInvalidManifest(archive);
    }

    @Test
    void 拒绝小数schemaVersion() {
        byte[] archive = zip(entry("skill.json",
                validManifest().replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.5")));

        assertInvalidManifest(archive);
    }

    @Test
    void 拒绝小数timeoutMillis() {
        byte[] archive = zip(entry("skill.json",
                validManifest().replace(
                        "\"path\": \"/quality\"",
                        "\"path\": \"/quality\",\n    \"timeoutMillis\": 2500.5")));

        assertInvalidManifest(archive);
    }
    @Test
    void rejectsNullHeaderValue() {
        byte[] archive = zip(entry("skill.json",
                validManifest().replace(
                        "\"path\": \"/quality\"",
                        "\"path\": \"/quality\",\n    \"headers\": {\"Authorization\": null}")));

        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("apiConfig.headers \u683c\u5f0f\u4e0d\u5408\u6cd5")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 拒绝超过256KB的skillJson() {
        String oversized = """
                {
                  "schemaVersion": 1,
                  "name": "quality_query",
                  "description": "%s",
                  "apiConfig": {
                    "baseUrl": "https://api.example.com",
                    "path": "/quality"
                  }
                }
                """.formatted("中".repeat(256 * 1024));

        assertThatThrownBy(() -> parser.parse(zip(entry("skill.json", oversized))))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("skill.json 解压后不能超过 256 KB")
                .extracting("code")
                .isEqualTo(413);
    }

    @Test
    void 拒绝超过16个条目() {
        List<Entry> entries = new ArrayList<>();
        entries.add(entry("skill.json", validManifest()));
        for (int index = 1; index <= 16; index++) {
            entries.add(entry("extra-" + index + ".txt", "extra"));
        }

        assertThatThrownBy(() -> parser.parse(zip(entries.toArray(Entry[]::new))))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 条目数不能超过 16")
                .extracting("code")
                .isEqualTo(413);
    }

    @Test
    void 支持UTF8中文描述() {
        byte[] archive = zip(entry("skill.json", """
                {
                  "schemaVersion": 1,
                  "name": "质量查询",
                  "description": "查询中文质量检验结果",
                  "readOnly": false,
                  "apiConfig": {
                    "enabled": false,
                    "baseUrl": "https://api.example.com",
                    "path": "/质量查询",
                    "method": "GET",
                    "headers": {
                      "X-说明": "中文请求头"
                    },
                    "timeoutMillis": 5000
                  }
                }
                """));

        AiSkillImportManifest manifest = parser.parse(archive);

        assertThat(manifest.name()).isEqualTo("质量查询");
        assertThat(manifest.description()).isEqualTo("查询中文质量检验结果");
        assertThat(manifest.readOnlyOrDefault()).isFalse();
        assertThat(manifest.apiConfig().enabledOrDefault()).isFalse();
        assertThat(manifest.apiConfig().headersOrDefault()).containsEntry("X-说明", "中文请求头");
    }

    @Test
    void 拒绝空白必填字段和非正数超时() {
        byte[] blankName = zip(entry("skill.json",
                validManifest().replace("\"quality_query\"", "\"  \"")));
        byte[] invalidTimeout = zip(entry("skill.json",
                validManifest().replace("\"path\": \"/quality\"", "\"path\": \"/quality\",\n    \"timeoutMillis\": 0")));

        assertThatThrownBy(() -> parser.parse(blankName))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("技能名称不能为空")
                .extracting("code")
                .isEqualTo(400);
        assertThatThrownBy(() -> parser.parse(invalidTimeout))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("timeoutMillis 必须大于 0")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void rejectsOversizedUnexpectedEntryWithoutDecompression() {
        byte[] archive = zip(
                entry("skill.json", validManifest()),
                entry("extra.txt", "x".repeat(256 * 1024 + 1)));

        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 只能包含根目录 skill.json")
                .extracting("code")
                .isEqualTo(400);
    }
    private void assertBadRequest(byte[] archive) {
        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    private void assertInvalidEntry(byte[] archive) {
        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 只能包含根目录 skill.json")
                .extracting("code")
                .isEqualTo(400);
    }

    private void assertInvalidManifest(byte[] archive) {
        assertThatThrownBy(() -> parser.parse(archive))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("skill.json 格式不合法")
                .extracting("code")
                .isEqualTo(400);
    }

    private static byte[] duplicateManifestArchive() {
        byte[] archive = zip(
                entry("skill.json", validManifest()),
                entry("skilL.json", validManifest()));
        return replaceAscii(archive, "skilL.json", "skill.json");
    }

    private static byte[] replaceAscii(byte[] source, String target, String replacement) {
        byte[] targetBytes = target.getBytes(StandardCharsets.US_ASCII);
        byte[] replacementBytes = replacement.getBytes(StandardCharsets.US_ASCII);
        for (int index = 0; index <= source.length - targetBytes.length; index++) {
            if (matchesAt(source, targetBytes, index)) {
                System.arraycopy(replacementBytes, 0, source, index, replacementBytes.length);
            }
        }
        return source;
    }

    private static boolean matchesAt(byte[] source, byte[] target, int offset) {
        for (int index = 0; index < target.length; index++) {
            if (source[offset + index] != target[index]) {
                return false;
            }
        }
        return true;
    }

    private static int findSignature(byte[] source, int signature) {
        for (int index = 0; index <= source.length - Integer.BYTES; index++) {
            int value = (source[index] & 0xFF)
                    | (source[index + 1] & 0xFF) << 8
                    | (source[index + 2] & 0xFF) << 16
                    | (source[index + 3] & 0xFF) << 24;
            if (value == signature) {
                return index;
            }
        }
        throw new IllegalStateException("ZIP signature not found");
    }
    private static String validManifest() {
        return """
                {
                  "schemaVersion": 1,
                  "name": "quality_query",
                  "description": "质量查询",
                  "apiConfig": {
                    "baseUrl": "https://api.example.com",
                    "path": "/quality"
                  }
                }
                """;
    }

    private static Entry entry(String name, String content) {
        return new Entry(name, content);
    }

    private static byte[] zip(Entry... entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (Entry entry : entries) {
                    zip.putNextEntry(new ZipEntry(entry.name()));
                    zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record Entry(String name, String content) {
    }
}
