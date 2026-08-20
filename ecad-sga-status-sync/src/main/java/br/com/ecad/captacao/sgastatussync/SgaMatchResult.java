package br.com.ecad.captacao.sgastatussync;

class SgaMatchResult {
    boolean found;
    double titleScore;
    double municipioScore;
    int candidatePosition;
    int candidatesEvaluated;
    int titleThresholdUsed;
    int municipioThresholdUsed;
    boolean thresholdElevated;
    double top1Score;
    boolean top1Rejected;
    String top1RejectedReason = "";
    boolean hadPromisingCandidates;

    int candidatePosition() { return candidatePosition; }
    double titleScore() { return titleScore; }
    double municipioScore() { return municipioScore; }
}
