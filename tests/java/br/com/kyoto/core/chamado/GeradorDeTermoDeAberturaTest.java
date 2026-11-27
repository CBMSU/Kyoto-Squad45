package br.com.kyoto.core.chamado;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeradorDeTermoDeAberturaTest {

    private static DadosDaReclamacao dadosCompletos() {
        return new DadosDaReclamacao(
                "PROTO-123",
                "Condominio Teste",
                "Morador Requerente",
                "Bloco A - Apt 101",
                "Bloco B - Apt 202",
                "Barulho",
                "Som alto apos as 22 horas",
                "Toda noite, a partir das 22h",
                Instant.parse("2026-11-27T12:00:00Z"));
    }

    @Test
    void textoContemOsDadosDaReclamacao() {
        TermoDeAbertura termo = new GeradorDeTermoDeAbertura().gerar(dadosCompletos());

        assertTrue(termo.texto().contains("Protocolo: PROTO-123"));
        assertTrue(termo.texto().contains("Condominio: Condominio Teste"));
        assertTrue(termo.texto().contains("Requerente: Morador Requerente"));
        assertTrue(termo.texto().contains("Unidade do requerente: Bloco A - Apt 101"));
        assertTrue(termo.texto().contains("Unidade reclamada: Bloco B - Apt 202"));
        assertTrue(termo.texto().contains("Tipo do problema: Barulho"));
        assertTrue(termo.texto().contains("Descricao do problema: Som alto apos as 22 horas"));
        assertTrue(termo.texto().contains("Quando acontece: Toda noite, a partir das 22h"));
        assertEquals("PROTO-123", termo.chamadoId());
    }

    @Test
    void camposOpcionaisEmBrancoViramNaoInformado() {
        DadosDaReclamacao dados = new DadosDaReclamacao(
                "PROTO-999", null, "  ", null, "Bloco C - Apt 303",
                null, "Porta batendo", "  ", Instant.parse("2026-11-27T12:00:00Z"));

        TermoDeAbertura termo = new GeradorDeTermoDeAbertura().gerar(dados);

        assertTrue(termo.texto().contains("Condominio: Nao informado"));
        assertTrue(termo.texto().contains("Requerente: Nao informado"));
        assertTrue(termo.texto().contains("Unidade do requerente: Nao informado"));
        assertTrue(termo.texto().contains("Tipo do problema: Nao informado"));
        assertTrue(termo.texto().contains("Quando acontece: Nao informado"));
    }

    @Test
    void pdfEhUmPdfValido() {
        TermoDeAbertura termo = new GeradorDeTermoDeAbertura().gerar(dadosCompletos());

        byte[] pdf = termo.pdf();
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String inicio = new String(pdf, 0, Math.min(8, pdf.length), java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(inicio.startsWith("%PDF-"), "cabecalho do PDF invalido");
        String fim = new String(pdf, Math.max(0, pdf.length - 8), Math.min(8, pdf.length), java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(fim.contains("%%EOF"), "marcador final do PDF ausente");
    }

    @Test
    void pdfContemADescricaoDoProblema() {
        TermoDeAbertura termo = new GeradorDeTermoDeAbertura().gerar(dadosCompletos());

        String pdfComoTexto = new String(termo.pdf(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(pdfComoTexto.contains("Som alto apos as 22 horas"));
    }

    @Test
    void portSalvaEBuscaPorChamado() {
        RepositorioDeTermos repositorio = new RepositorioDeTermosEmMemoria();
        TermoDeAbertura termo = new GeradorDeTermoDeAbertura().gerar(dadosCompletos());

        repositorio.salvar(termo);

        Optional<TermoDeAbertura> encontrado = repositorio.buscarPorChamado("PROTO-123");
        assertTrue(encontrado.isPresent());
        assertEquals(termo.id(), encontrado.get().id());
        assertArrayEquals(termo.pdf(), encontrado.get().pdf());

        assertTrue(repositorio.buscar(termo.id()).isPresent());
        assertFalse(repositorio.buscar("inexistente").isPresent());
        assertFalse(repositorio.buscarPorChamado("sem-termo").isPresent());
    }

    @Test
    void dadosNulosLancamExcecao() {
        assertThrows(NullPointerException.class,
                () -> new GeradorDeTermoDeAbertura().gerar(null));
    }

    @Test
    void protocoloEmBrancoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new DadosDaReclamacao(
                "   ", "Condominio", "Nome", null, "Bloco B - Apt 202",
                null, "Descricao", null, Instant.parse("2026-11-27T12:00:00Z")));
    }

    @Test
    void descricaoEmBrancoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new DadosDaReclamacao(
                "PROTO-1", "Condominio", "Nome", null, "Bloco B - Apt 202",
                null, "   ", null, Instant.parse("2026-11-27T12:00:00Z")));
    }

    @Test
    void geradoEmNuloUsaInstanteAtual() {
        DadosDaReclamacao dados = new DadosDaReclamacao(
                "PROTO-7", "Condominio", "Nome", null, "Bloco B - Apt 202",
                null, "Descricao", null, null);

        TermoDeAbertura termo = new GeradorDeTermoDeAbertura().gerar(dados);

        assertNotNull(termo.geradoEm());
    }
}
