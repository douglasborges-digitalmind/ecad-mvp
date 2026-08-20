package br.com.ecad.captacao.processingengine;

record DocumentContentPayload(
    byte[] rawContent,
    String contentType,
    byte[] mediaBytes,
    String mimeType,
    String textContent,
    boolean isPdf,
    boolean hasUsableText) {
}
