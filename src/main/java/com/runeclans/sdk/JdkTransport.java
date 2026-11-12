package com.runeclans.sdk;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** The default {@link Transport}: one JDK {@link HttpClient} (one connection pool, HTTP/2 where the hub offers it) for every call. */
public final class JdkTransport implements Transport {
    private final HttpClient http;

    public JdkTransport(Duration connectTimeout) {
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Response send(Request request) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(request.uri()).timeout(request.timeout());
        request.headers().forEach(b::header);
        b.method(request.method(), request.body() == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(request.body()));
        HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Response(r.statusCode(), r.headers().map(), r.body());
    }
}
