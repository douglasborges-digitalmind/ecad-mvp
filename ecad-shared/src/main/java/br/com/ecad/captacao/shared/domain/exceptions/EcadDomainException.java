package br.com.ecad.captacao.shared.domain.exceptions;

/**
 * Exceção base do domínio ECAD. Todas as exceções específicas de negócio
 * devem estender esta classe, permitindo tratamento centralizado e
 * rastreabilidade consistente.
 */
public class EcadDomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EcadDomainException(String message) {
        super(message);
    }

    public EcadDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}