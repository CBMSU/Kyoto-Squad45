package br.com.kyoto.core.chamado;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

public class GeradorDeTermoDeAbertura {

    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public TermoDeAbertura gerar(DadosDaReclamacao dados) {
        Objects.requireNonNull(dados, "dados");
        String texto = montarTexto(dados);
        byte[] pdf;
        try {
            pdf = GeradorDePdf.gerar(texto);
        } catch (RuntimeException e) {
            throw new TermoNaoGeradoException("Falha ao gerar o PDF do termo", e);
        }
        return new TermoDeAbertura(
                UUID.randomUUID().toString(),
                dados.protocolo(),
                dados.geradoEm(),
                texto,
                pdf);
    }

    private static String montarTexto(DadosDaReclamacao dados) {
        StringBuilder sb = new StringBuilder();
        sb.append("TERMO DE ABERTURA DE RECLAMACAO\n\n");
        sb.append("Protocolo: ").append(dados.protocolo()).append("\n");
        sb.append("Data de geracao: ").append(FORMATO_DATA.format(dados.geradoEm())).append("\n");
        sb.append("Condominio: ").append(valorOuNaoInformado(dados.condominio())).append("\n\n");

        sb.append("Requerente: ").append(valorOuNaoInformado(dados.requerenteNome())).append("\n");
        sb.append("Unidade do requerente: ").append(valorOuNaoInformado(dados.requerenteUnidade())).append("\n");
        sb.append("Unidade reclamada: ").append(valorOuNaoInformado(dados.requeridoUnidade())).append("\n\n");

        sb.append("Tipo do problema: ").append(valorOuNaoInformado(dados.tipoProblema())).append("\n");
        sb.append("Descricao do problema: ").append(dados.descricao()).append("\n");
        sb.append("Quando acontece: ").append(valorOuNaoInformado(dados.quandoAcontece())).append("\n\n");

        sb.append("Status inicial: Aberto\n");
        return sb.toString();
    }

    private static String valorOuNaoInformado(String valor) {
        return (valor == null || valor.isBlank()) ? "Nao informado" : valor.strip();
    }
}
