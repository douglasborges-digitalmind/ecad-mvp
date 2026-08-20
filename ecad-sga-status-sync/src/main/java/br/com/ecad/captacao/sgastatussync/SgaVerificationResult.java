package br.com.ecad.captacao.sgastatussync;

import br.com.ecad.captacao.shared.domain.enums.StatusSGA;

record SgaVerificationResult(StatusSGA status, SgaMatchResult match, int candidatesCount, boolean fromCache) {
}
