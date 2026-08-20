package br.com.ecad.captacao.sgastatussync;

interface SgaApiClient {
    SgaVerificationResult verificarEvento(SgaEventQuery query) throws Exception;
}
