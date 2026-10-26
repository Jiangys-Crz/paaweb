package com.cwnupaa.backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Private, bounded process-local results. Callers must authorize every request before consulting it. */
final class CertificatePdfCache {
    private final Cache<String, byte[]> cache = Caffeine.newBuilder()
            .maximumWeight(32 * 1024 * 1024)
            .weigher((String key, byte[] pdf) -> pdf.length)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    static String key(File template, String... values) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(template.toPath(), BasicFileAttributes.class);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        update(digest, template.getCanonicalPath());
        update(digest, attributes.size() + ":" + attributes.lastModifiedTime() + ":" + attributes.fileKey());
        for (String value : values) update(digest, value);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
        digest.update(bytes);
    }

    byte[] get(String key, Generator generator) throws Exception {
        try {
            // Caffeine's atomic load coalesces simultaneous preview/download requests.
            return cache.get(key, ignored -> {
                try {
                    return generator.generate();
                } catch (Exception e) {
                    throw new GenerationFailure(e);
                }
            });
        } catch (GenerationFailure e) {
            throw (Exception) e.getCause();
        }
    }

    @FunctionalInterface
    interface Generator {
        byte[] generate() throws Exception;
    }

    private static final class GenerationFailure extends RuntimeException {
        GenerationFailure(Exception cause) { super(cause); }
    }
}
