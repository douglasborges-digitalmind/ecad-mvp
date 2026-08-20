package br.com.ecad.captacao.controlcenter.services;

import java.io.InputStream;
import java.util.List;

public class NullEmailService implements EmailService {
    @Override
    public void enviarPlanilha(InputStream planilha, String nomeArquivo, String assunto, String corpoHtml, List<String> destinatarios) {
    }
}
