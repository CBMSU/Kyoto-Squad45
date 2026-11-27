package br.com.kyoto.core.chamado;

import java.time.Instant;
import java.util.Objects;

public record DadosDaReclamacao(
        String protocolo,
        String condominio,
        String requerenteNome,
        String requerenteUnidade,
        String requeridoUnidade,
        String tipoProblema,
        String descricao,
        String quandoAcontece,
        Instant geradoEm) {

    public DadosDaReclamacao {
        Objects.requireNonNull(protocolo, "protocolo");
        Objects.requireNonNull(descricao, "descricao");
        if (protocolo.isBlank()) {
            throw new IllegalArgumentException("protocolo é obrigatório");
        }
        if (descricao.isBlank()) {
            throw new IllegalArgumentException("descricao é obrigatória");
        }
        if (geradoEm == null) {
            geradoEm = Instant.now();
        }
    }
}
