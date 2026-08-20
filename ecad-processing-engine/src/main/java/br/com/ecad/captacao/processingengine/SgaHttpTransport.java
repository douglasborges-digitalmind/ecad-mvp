package br.com.ecad.captacao.processingengine;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

interface SgaHttpTransport {
    SgaHttpResponse send(String method, URI uri, Map<String, String> headers, String body, Duration timeout) throws Exception;
}
