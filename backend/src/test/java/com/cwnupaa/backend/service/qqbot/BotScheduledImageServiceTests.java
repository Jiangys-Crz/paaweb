package com.cwnupaa.backend.service.qqbot;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BotScheduledImageServiceTests {
    @Test void acceptsOnlyOfficialHttpsImagesAndAllowsQueries() {
        for (String safe : List.of("https://example.com/logo.png", "https://www.example.com:443/api/file/image/1?public=yes"))
            assertDoesNotThrow(() -> BotScheduledImageService.imageUri(safe));
        for (String unsafe : List.of(
                "http://example.com/logo.png", "https://example.com:8443/logo.png",
                "https://127.0.0.1/logo.png", "https://example.com.evil.invalid/logo.png",
                "https://evil.invalid/logo.png", "https://user:pass@example.com/logo.png",
                "https://example.com/logo.png#fragment", "file:///etc/passwd",
                "base64://image", "[CQ:image,file=https://example.com/logo.png]"))
            assertThrows(IllegalArgumentException.class, () -> BotScheduledImageService.imageUri(unsafe), unsafe);
    }

    @Test void validatesAllUrlsAndImageCountBeforeAnyNetworkOperation() {
        HttpClient http = mock(HttpClient.class);
        var service = new BotScheduledImageService(http, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(List.of("https://example.com/logo.png", "https://evil.invalid/image.png")));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(java.util.Collections.nCopies(6, "https://example.com/logo.png")));
        assertEquals(List.of(), service.prepare(List.of()));
        verifyNoInteractions(http);
    }

    @Test void convertsDecodedImageToPngBase64InsteadOfReturningRemoteUrl() throws Exception {
        var raw = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", raw);
        HttpClient http = mockResponse(200, raw.toByteArray(), Map.of());
        var result = new BotScheduledImageService(http, Duration.ofSeconds(1))
                .prepare(List.of("https://example.com/api/file/image/1?token=public"));
        assertEquals(1, result.size());
        assertTrue(result.getFirst().startsWith("base64://"));
        byte[] png = Base64.getDecoder().decode(result.getFirst().substring("base64://".length()));
        assertArrayEquals(new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 }, java.util.Arrays.copyOf(png, 8));
        assertEquals(8, ImageIO.read(new java.io.ByteArrayInputStream(png)).getWidth());
        verify(http, times(1)).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test void rejectsRedirectsNonImagesAndOversizedDimensions() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new BotScheduledImageService(mockResponse(302, new byte[0], Map.of("Location", List.of("http://127.0.0.1/metadata"))), Duration.ofSeconds(1))
                .prepare(List.of("https://example.com/logo.png")));
        assertThrows(IllegalArgumentException.class, () -> new BotScheduledImageService(mockResponse(200, "<html>login required</html>".getBytes(), Map.of()), Duration.ofSeconds(1))
                .prepare(List.of("https://example.com/logo.png")));
        var raw = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", raw);
        assertThrows(IllegalArgumentException.class, () -> new BotScheduledImageService(mockResponse(200, raw.toByteArray(), Map.of()), Duration.ofSeconds(1))
                .prepare(List.of("https://example.com/logo.png")));
    }

    @Test void bodyLimitCountsChunksEvenWithoutContentLengthAndCancelsDownload() {
        var subscriber = new BotScheduledImageService.BoundedBodySubscriber(null);
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.allocate(BotScheduledImageService.MAX_IMAGE_BYTES)));
        assertFalse(subscriber.getBody().toCompletableFuture().isDone());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] { 1 })));
        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
        verify(subscription).cancel();
    }

    @Test void rejectsDeclaredOversizedResponsesBeforeReadingBody() {
        HttpClient http = mockResponse(200, new byte[0], Map.of("Content-Length", List.of(String.valueOf(BotScheduledImageService.MAX_IMAGE_BYTES + 1))));
        assertThrows(IllegalArgumentException.class, () -> new BotScheduledImageService(http, Duration.ofSeconds(1))
                .prepare(List.of("https://example.com/logo.png")));
    }

    @Test void totalDeadlineCancelsBodyThatNeverCompletes() {
        HttpClient http = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> response = new CompletableFuture<>();
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(response);
        var service = new BotScheduledImageService(http, Duration.ofMillis(50));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThrows(IllegalArgumentException.class,
                () -> service.prepare(List.of("https://example.com/logo.png"))));
        assertTrue(response.isCancelled());
    }

    private HttpClient mockResponse(int status, byte[] bytes, Map<String, List<String>> headers) {
        HttpClient http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call -> {
            HttpResponse.BodyHandler<byte[]> handler = call.getArgument(1);
            HttpResponse.ResponseInfo info = mock(HttpResponse.ResponseInfo.class);
            when(info.statusCode()).thenReturn(status);
            when(info.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
            var subscriber = handler.apply(info);
            var response = new CompletableFuture<HttpResponse<byte[]>>();
            subscriber.getBody().whenComplete((data, error) -> {
                if (error != null) response.completeExceptionally(error);
                else {
                    @SuppressWarnings("unchecked") HttpResponse<byte[]> result = mock(HttpResponse.class);
                    when(result.statusCode()).thenReturn(status);
                    when(result.body()).thenReturn(data);
                    response.complete(result);
                }
            });
            subscriber.onSubscribe(mock(Flow.Subscription.class));
            if (bytes.length > 0) subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
            subscriber.onComplete();
            return response;
        });
        return http;
    }
}
