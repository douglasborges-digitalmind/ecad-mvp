package br.com.ecad.captacao.shared.domain.exceptions;

/**
 * Falha na extração de dados via IA (resposta inválida, erro de parsing,
 * provedor indisponível após retry).
 */
public class ExtractionException extends EcadDomainException {

    private static final long serialVersionUID = 1L;

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}