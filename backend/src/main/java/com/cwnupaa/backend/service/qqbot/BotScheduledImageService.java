package com.cwnupaa.backend.service.qqbot;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Downloads trusted website images locally; NapCat never fetches an administrator-supplied URL. */
@Service
public class BotScheduledImageService {
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private final HttpClient http;
    private final Duration deadline;

    public BotScheduledImageService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), Duration.ofSeconds(10));
    }

    BotScheduledImageService(HttpClient http, Duration deadline) {
        this.http = http;
        this.deadline = deadline;
    }

    public List<String> prepare(List<String> urls) {
        if (urls == null || urls.isEmpty()) return List.of();
        if (urls.size() > 5) throw new IllegalArgumentException("每条消息最多5张图片");
        // Validate the entire list before making any network request.
        List<URI> images = urls.stream().map(BotScheduledImageService::imageUri).toList();
        long endsAt = System.nanoTime() + deadline.toNanos();
        List<String> result = new ArrayList<>();
        for (URI uri : images) {
            byte[] bytes = download(uri, endsAt);
            try {
                var image = BotCardRenderer.decodeImage(bytes);
                if (image == null) throw new IOException("Unsupported image");
                var output = new LimitedOutput();
                // Re-encoding removes non-image payloads and metadata before OneBot receives the file.
                if (!ImageIO.write(image, "png", output)) throw new IOException("Unsupported PNG encoder");
                if (System.nanoTime() >= endsAt) throw new IllegalArgumentException("图片处理超时，请使用较小的官网图片");
                result.add("base64://" + Base64.getEncoder().encodeToString(output.toByteArray()));
            } catch (IOException e) {
                throw new IllegalArgumentException("图片无效、尺寸过大或转换后的图片超过5MiB");
            }
        }
        return List.copyOf(result);
    }

    static URI imageUri(String value) {
        try {
            URI uri = URI.create(value);
            if (value.length() > 2048 || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || !com.cwnupaa.backend.utils.DeploymentUrls.imageHosts().contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
            return uri;
        } catch (Exception e) {
            throw new IllegalArgumentException("图片只允许本站或管理员配置的白名单域名的公开 HTTPS 链接");
        }
    }

    private byte[] download(URI uri, long endsAt) {
        long remaining = endsAt - System.nanoTime();
        if (remaining <= 0) throw new IllegalArgumentException("图片下载超时，请稍后重试");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(remaining))
                .header("Accept", "image/*").GET().build();
        var active = new AtomicReference<BoundedBodySubscriber>();
        CompletableFuture<HttpResponse<byte[]>> response = http.sendAsync(request, info -> {
            String failure = info.statusCode() == 200 ? null : "图片不是可公开访问的文件，且不允许重定向";
            if (failure == null) {
                try {
                    if (info.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_IMAGE_BYTES)
                        failure = "图片超过5MiB";
                } catch (NumberFormatException e) { failure = "图片响应格式不合法"; }
            }
            var subscriber = new BoundedBodySubscriber(failure);
            active.set(subscriber);
            return subscriber;
        });
        try {
            // Unlike an InputStream body, this future completes only after the bounded body finishes.
            HttpResponse<byte[]> result = response.get(Math.max(1, endsAt - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (result.statusCode() != 200 || result.body() == null || result.body().length > MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("图片下载失败或超过5MiB");
            return result.body();
        } catch (InterruptedException e) {
            response.cancel(true);
            if (active.get() != null) active.get().cancel();
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("图片下载已中断");
        } catch (TimeoutException | ExecutionException e) {
            response.cancel(true);
            if (active.get() != null) active.get().cancel();
            throw new IllegalArgumentException("图片下载失败或超时，请确认官网图片可公开访问且不超过5MiB");
        }
    }

    /** Counts actual received bytes; an absent or false Content-Length cannot bypass the cap. */
    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private volatile Flow.Subscription subscription;

        BoundedBodySubscriber(String failure) {
            if (failure != null) body.completeExceptionally(new IOException(failure));
        }

        @Override public CompletionStage<byte[]> getBody() { return body; }

        @Override public void onSubscribe(Flow.Subscription next) {
            if (subscription != null) { next.cancel(); return; }
            subscription = next;
            if (body.isDone()) next.cancel(); else next.request(1);
        }

        @Override public void onNext(List<ByteBuffer> items) {
            if (body.isDone()) { cancel(); return; }
            for (ByteBuffer item : items) {
                if ((long) bytes.size() + item.remaining() > MAX_IMAGE_BYTES) {
                    body.completeExceptionally(new IOException("Image response exceeds 5MiB"));
                    cancel();
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.write(chunk, 0, chunk.length);
            }
            if (subscription != null) subscription.request(1);
        }

        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
        void cancel() { if (subscription != null) subscription.cancel(); }
    }

    private static final class LimitedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) {
            check(1);
            super.write(value);
        }
        @Override public synchronized void write(byte[] value, int offset, int length) {
            check(length);
            super.write(value, offset, length);
        }
        private void check(int length) {
            if ((long) count + length > MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("转换后的图片超过5MiB，请使用较小的官网图片");
        }
    }
}
