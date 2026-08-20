package br.com.ecad.captacao.processingengine;

@FunctionalInterface
interface CheckpointAction {
    void updateCheckpoint() throws Exception;
}
