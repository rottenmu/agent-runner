package com.zimo.starter.ai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地沙箱文件系统单测（dsh A7 共享执行世界）：读写/列表/删除/越界拒绝/白名单。
 */
class LocalSandboxFileSystemTest {

    @TempDir
    Path tempDir;

    private LocalSandboxFileSystem fileSystem() {
        return new LocalSandboxFileSystem(tempDir.toString(), Set.of());
    }

    @Test
    void writesAndReadsFileWithinWorkspace() {
        LocalSandboxFileSystem fs = fileSystem();

        SandboxFileResult write = fs.execute(new SandboxFileOp("write", "notes/hello.txt", "hi there"));
        assertThat(write.success()).isTrue();

        SandboxFileResult read = fs.execute(new SandboxFileOp("read", "notes/hello.txt"));
        assertThat(read.success()).isTrue();
        assertThat(read.content()).isEqualTo("hi there");
    }

    @Test
    void listsDirectoryEntries() {
        LocalSandboxFileSystem fs = fileSystem();
        fs.execute(new SandboxFileOp("write", "a.txt", "1"));
        fs.execute(new SandboxFileOp("write", "sub/b.txt", "2"));

        SandboxFileResult list = fs.execute(new SandboxFileOp("list", ""));
        assertThat(list.success()).isTrue();
        assertThat(list.entries()).contains("a.txt", "sub/");
    }

    @Test
    void existsAndDelete() {
        LocalSandboxFileSystem fs = fileSystem();
        fs.execute(new SandboxFileOp("write", "temp.txt", "x"));

        assertThat(fs.execute(new SandboxFileOp("exists", "temp.txt")).exists()).isTrue();
        assertThat(fs.execute(new SandboxFileOp("delete", "temp.txt")).success()).isTrue();
        assertThat(fs.execute(new SandboxFileOp("exists", "temp.txt")).exists()).isFalse();
    }

    @Test
    void rejectsAbsolutePath() {
        LocalSandboxFileSystem fs = fileSystem();
        String absolute = Path.of("C:/windows/system32").toString();
        SandboxFileResult result = fs.execute(new SandboxFileOp("read", absolute));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("禁止绝对路径");
    }

    @Test
    void rejectsParentTraversal() {
        LocalSandboxFileSystem fs = fileSystem();
        SandboxFileResult result = fs.execute(new SandboxFileOp("read", "../secret.txt"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("禁止路径越界");
    }

    @Test
    void rejectsUnknownOpAndEmptyPath() {
        LocalSandboxFileSystem fs = fileSystem();
        assertThat(fs.execute(new SandboxFileOp("chmod", "a.txt")).success()).isFalse();
        assertThat(fs.execute(new SandboxFileOp("read", "")).success()).isFalse();
    }

    @Test
    void readMissingFileFails() {
        LocalSandboxFileSystem fs = fileSystem();
        SandboxFileResult result = fs.execute(new SandboxFileOp("read", "nope.txt"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("文件不存在");
    }

    @Test
    void writeEnforcesExtensionWhitelist() {
        LocalSandboxFileSystem fs = new LocalSandboxFileSystem(
                tempDir.toString(), Set.of("txt", "md"));
        assertThat(fs.execute(new SandboxFileOp("write", "ok.txt", "1")).success()).isTrue();
        assertThat(fs.execute(new SandboxFileOp("write", "ok.md", "2")).success()).isTrue();
        SandboxFileResult blocked = fs.execute(new SandboxFileOp("write", "evil.exe", "x"));
        assertThat(blocked.success()).isFalse();
        assertThat(blocked.error()).contains("扩展名不在白名单");
    }

    @Test
    void deleteRejectsWorkspaceRoot() {
        LocalSandboxFileSystem fs = fileSystem();
        SandboxFileResult result = fs.execute(new SandboxFileOp("delete", ""));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("禁止删除工作区根目录");
    }

    @Test
    void normalizeSafeStripsSlashesAndRejectsTraversal() {
        assertThat(SandboxFileSystem.normalizeSafe("/root", "a/b/c")).isEqualTo("a/b/c");
        assertThat(SandboxFileSystem.normalizeSafe("/root", "./a")).isEqualTo("a");
        assertThat(SandboxFileSystem.normalizeSafe("/root", "a\\b\\c")).isEqualTo("a/b/c");
        assertThatThrownBy(() -> SandboxFileSystem.normalizeSafe("/root", "../x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SandboxFileSystem.normalizeSafe("/root", "/abs"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}