package br.com.ecad.captacao.controlcenter;

import java.util.NoSuchElementException;

import br.com.ecad.captacao.shared.domain.exceptions.EcadDomainException;
import br.com.ecad.captacao.shared.domain.exceptions.ExtractionException;
import br.com.ecad.captacao.shared.domain.exceptions.SgaException;
import br.com.ecad.captacao.shared.domain.exceptions.BlobStorageException;
import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requisicao invalida");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ProblemDetail> notFound(NoSuchElementException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso nao encontrado");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ExtractionException.class)
    ResponseEntity<ProblemDetail> extractionFailure(ExtractionException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Falha na extracao por IA");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(SgaException.class)
    ResponseEntity<ProblemDetail> sgaFailure(SgaException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Falha na comunicacao com o SGA");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    @ExceptionHandler(BlobStorageException.class)
    ResponseEntity<ProblemDetail> blobFailure(BlobStorageException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Falha no armazenamento de blobs");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    @ExceptionHandler(ProcessingException.class)
    ResponseEntity<ProblemDetail> processingFailure(ProcessingException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("Falha no pipeline de processamento");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @ExceptionHandler(EcadDomainException.class)
    ResponseEntity<ProblemDetail> domainFailure(EcadDomainException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("Erro de dominio");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
