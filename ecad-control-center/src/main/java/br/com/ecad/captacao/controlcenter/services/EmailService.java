package br.com.ecad.captacao.controlcenter.services;

import java.io.InputStream;
import java.util.List;

public interface EmailService {
    void enviarPlanilha(InputStream planilha, String nomeArquivo, String assunto, String corpoHtml, List<String> destinatarios) throws Exception;
}
