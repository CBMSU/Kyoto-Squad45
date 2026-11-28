package br.com.kyoto.core.conversa;

import java.util.Objects;

/** {@code resumo} aparece para o morador: não pode ter dados pessoais do outro lado. */
public record ParticipacaoEmMediacao(String mediacaoId, PapelNaMediacao papel, String resumo) {
    public ParticipacaoEmMediacao {
        Objects.requireNonNull(mediacaoId, "mediacaoId");
        Objects.requireNonNull(papel, "papel");
        Objects.requireNonNull(resumo, "resumo");
    }
}
