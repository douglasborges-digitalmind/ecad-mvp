package br.com.ecad.captacao.shared.domain.exceptions;

/**
 * Falha na comunicação com o sistema SGA (autenticação, timeout, HTTP != 2xx).
 */
public class SgaException extends EcadDomainException {

    private static final long serialVersionUID = 1L;

    public SgaException(String message) {
        super(message);
    }

    public SgaException(String message, Throwable cause) {
        super(message, cause);
    }
}