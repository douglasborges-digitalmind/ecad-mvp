package br.com.ecad.captacao.shared.domain.exceptions;

/**
 * Falha geral no pipeline de processamento de documentos.
 */
public class ProcessingException extends EcadDomainException {

    private static final long serialVersionUID = 1L;

    public ProcessingException(String message) {
        super(message);
    }

    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}