package br.com.ecad.captacao.processingengine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
class JavaNetSgaHttpTransport implements SgaHttpTransport {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public SgaHttpResponse send(String method, URI uri, Map<String, String> headers, String body, Duration timeout) throws Exception {
        var builder = HttpRequest.newBuilder(uri).timeout(timeout);
        for (var entry : headers.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        var publisher = body == null || body.isBlank()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
        var request = builder.method(method, publisher).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new SgaHttpResponse(response.statusCode(), response.body());
    }
}