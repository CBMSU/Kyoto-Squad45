package br.com.kyoto.core.chamado;

import java.time.Instant;
import java.util.Objects;

public record TermoDeAbertura(
        String id,
        String chamadoId,
        Instant geradoEm,
        String texto,
        byte[] pdf) {

    public TermoDeAbertura {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(chamadoId, "chamadoId");
        Objects.requireNonNull(geradoEm, "geradoEm");
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(pdf, "pdf");
    }
}
