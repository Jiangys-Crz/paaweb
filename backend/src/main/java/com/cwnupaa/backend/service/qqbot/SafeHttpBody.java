package com.cwnupaa.backend.service.qqbot;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounded subscriber: a large or endless remote body is cancelled before it can fill the heap. */
public final class SafeHttpBody {
    private SafeHttpBody() {}

    public static HttpResponse.BodyHandler<byte[]> limited(int maximumBytes) {
        if (maximumBytes <= 0) throw new IllegalArgumentException("Body limit must be positive");
        return ignored -> new Subscriber(maximumBytes);
    }

    private static final class Subscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximumBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private Subscriber(int maximumBytes) { this.maximumBytes = maximumBytes; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) {
            if (subscription != null) { value.cancel(); return; }
            subscription = value; value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                if (remaining > maximumBytes - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException("HTTP response exceeds body limit"));
                    return;
                }
                byte[] part = new byte[remaining]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
