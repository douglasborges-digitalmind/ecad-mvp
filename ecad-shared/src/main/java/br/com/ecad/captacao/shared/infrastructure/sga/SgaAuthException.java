package br.com.ecad.captacao.shared.infrastructure.sga;

/**
 * Exceção lançada quando a autenticação com o SGA falha.
 */
public class SgaAuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SgaAuthException(String message) {
        super(message);
    }

    public SgaAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
