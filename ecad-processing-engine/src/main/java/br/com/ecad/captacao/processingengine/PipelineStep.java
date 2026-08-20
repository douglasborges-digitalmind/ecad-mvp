package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;

/**
 * Um passo individual no pipeline de processamento de documentos.
 * Cada implementação tem uma responsabilidade única.
 */
interface PipelineStep {

    /**
     * Executa este passo do pipeline.
     * @param context estado compartilhado do pipeline
     * @throws ProcessingException se o passo falhar de forma irrecuperável
     */
    void execute(PipelineContext context) throws ProcessingException;

    /**
     * Compensa/desfaz este passo em caso de falha de um passo posterior.
     * Implementação padrão: no-op.
     */
    default void compensate(PipelineContext context) {
    }
}