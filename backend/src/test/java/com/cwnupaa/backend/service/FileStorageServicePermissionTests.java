package com.cwnupaa.backend.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关容器里的 Nginx 以 nginx 用户读取挂载的 uploads，落盘权限必须是 0644/0755。
 * 这些用例锁住"JDK 临时文件 0600 被 Files.move 带到目标文件"导致的 403 回归。
 */
class FileStorageServicePermissionTests {

    @Test
    void storedFileAndItsDirectoryBecomeReadableByGateway() throws Exception {
        Path dir = Files.createTempDirectory("cwnupaa-perm-");
        Path file = dir.resolve("attachment.doc");
        Files.write(file, "content".getBytes());
        // Files.createTempFile + Files.move 之后的真实权限
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));

        FileStorageService.makeReadableByGateway(file);

        assertThat(permissions(file)).isEqualTo("rw-r--r--");
        assertThat(permissions(dir)).isEqualTo("rwxr-xr-x");
    }

    @Test
    void legacyRestrictedFileIsRepairedBeforeNginxHandoff() throws Exception {
        Path base = Files.createTempDirectory("cwnupaa-uploads-");
        Path nested = base.resolve("id-photos");
        Files.createDirectories(nested);
        Path file = nested.resolve("card.jpg");
        Files.write(file, "content".getBytes());
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(nested, PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(base, PosixFilePermissions.fromString("rwx------"));

        assertThat(FileStorageService.ensureReadableByGateway(base, file)).isTrue();

        assertThat(permissions(file)).isEqualTo("rw-r--r--");
        assertThat(permissions(nested)).isEqualTo("rwxr-xr-x");
        assertThat(permissions(base)).isEqualTo("rwxr-xr-x");
    }

    @Test
    void alreadyReadableFileIsLeftUntouched() throws Exception {
        Path base = Files.createTempDirectory("cwnupaa-uploads-");
        Path file = base.resolve("notice.pdf");
        Files.write(file, "content".getBytes());
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        assertThat(FileStorageService.ensureReadableByGateway(base, file)).isTrue();

        assertThat(permissions(file)).isEqualTo("rw-r--r--");
    }

    private static String permissions(Path path) throws Exception {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
        return PosixFilePermissions.toString(perms);
    }
}
