package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.FileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FileStorageServiceTests {

    @TempDir
    Path uploadDirectory;

    @Test
    void createsEncodedInternalRedirectOnlyForExistingLocalFile() throws Exception {
        FileStorageService service = new FileStorageService(mock(SystemConfigService.class));
        ReflectionTestUtils.setField(service, "uploadPath", uploadDirectory.toString());

        Path filePath = uploadDirectory.resolve("id-photos/file name.png");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "test");

        FileSystem file = new FileSystem();
        file.setStorageType(FileStorageService.LOCAL);
        file.setStorageKey("id-photos/file name.png");

        assertThat(service.internalRedirectUri(file, true))
                .isEqualTo("/_protected_downloads/id-photos/file%20name.png");
        assertThat(service.internalRedirectUri(file, false))
                .isEqualTo("/_protected_media/id-photos/file%20name.png");

        file.setStorageKey("missing.png");
        assertThat(service.internalRedirectUri(file, true)).isNull();
    }
}
