package com.cwnupaa.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificatePdfCacheTests {
    @TempDir Path temp;

    @Test
    void simultaneousPreviewAndDownloadOnlyGenerateOnce() throws Exception {
        var cache = new CertificatePdfCache();
        var calls = new AtomicInteger();
        var ready = new CountDownLatch(6);
        var start = new CountDownLatch(1);
        byte[] expected = {1, 2, 3};
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ArrayList<Future<byte[]>>();
            for (int i = 0; i < 6; i++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return cache.get("same-certificate", () -> {
                        calls.incrementAndGet();
                        return expected;
                    });
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<byte[]> request : requests) assertThat(request.get(5, TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void failedGenerationCanBeRetriedAndDifferentCertificatesStaySeparate() throws Exception {
        var cache = new CertificatePdfCache();
        assertThatThrownBy(() -> cache.get("first", () -> { throw new IOException("invalid template"); }))
                .isInstanceOf(IOException.class).hasMessage("invalid template");
        assertThat(cache.get("first", () -> new byte[]{1})).containsExactly((byte) 1);
        assertThat(cache.get("second", () -> new byte[]{2})).containsExactly((byte) 2);
        assertThat(cache.get("first", () -> { throw new AssertionError("unexpected generation"); }))
                .containsExactly((byte) 1);
    }

    @Test
    void keysSeparateAmbiguousValuesAndNoticeReplacedTemplates() throws Exception {
        var template = temp.resolve("template.pdf");
        Files.writeString(template, "original");
        String key = CertificatePdfCache.key(template.toFile(), "ab", "c");
        assertThat(CertificatePdfCache.key(template.toFile(), "a", "bc")).isNotEqualTo(key);
        assertThat(CertificatePdfCache.key(template.toFile(), "ab", null)).isNotEqualTo(key);
        assertThat(CertificatePdfCache.key(template.toFile(), "ab", "")).isNotEqualTo(key);
        Files.setLastModifiedTime(template, FileTime.fromMillis(Files.getLastModifiedTime(template).toMillis() + 2000));
        assertThat(CertificatePdfCache.key(template.toFile(), "ab", "c")).isNotEqualTo(key);
        Files.delete(template);
        assertThatThrownBy(() -> CertificatePdfCache.key(template.toFile(), "ab", "c"))
                .isInstanceOf(IOException.class);
    }
}
