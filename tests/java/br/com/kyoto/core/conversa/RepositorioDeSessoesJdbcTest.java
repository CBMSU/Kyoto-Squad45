package br.com.kyoto.core.conversa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositorioDeSessoesJdbcTest {

    private static final MoradorDaConversa ANA = new MoradorDaConversa("m1", "Ana", "5579999990001");
    private static final Instant AGORA = Instant.parse("2026-11-27T12:00:00Z");

    private Path diretorio;
    private String url;

    @BeforeEach
    void prepararBanco() throws IOException {
        diretorio = Files.createTempDirectory("kyoto-sessoes");
        url = "jdbc:h2:file:" + diretorio.resolve("sessoes").toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        criarTabelas();
    }

    @AfterEach
    void apagarBanco() throws IOException {
        try (Stream<Path> arquivos = Files.walk(diretorio)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test
    void restoresCompleteSessionAfterRestart() {
        SessaoConversa sessao = new SessaoConversa(ANA);
        sessao.entrarNaMediacao(new ParticipacaoEmMediacao(
                "med-1", PapelNaMediacao.CITADO, "Barulho na noite"));
        sessao.registrarDado("descricao", "Barulho depois das 22h");
        sessao.registrarDado("unidade", "B-201");
        sessao.guardarMensagemPendente("A vaga é minha");
        sessao.registrarMensagem(AGORA);

        repositorio().salvar(sessao);

        SessaoConversa recuperada = repositorio().buscar(ANA.telefone()).orElseThrow();

        assertEquals(ANA, recuperada.morador());
        assertEquals(EtapaConversa.EM_MEDIACAO, recuperada.etapa());
        assertEquals(sessao.mediacoes(), recuperada.mediacoes());
        assertEquals(sessao.mediacaoAtiva(), recuperada.mediacaoAtiva());
        assertEquals(sessao.dadosColetados(), recuperada.dadosColetados());
        assertEquals(Optional.of("A vaga é minha"), recuperada.mensagemPendente());
        assertEquals(Optional.of(AGORA), recuperada.ultimaMensagem());
    }

    @Test
    void returnsEmptyWhenPhoneIsUnknown() {
        assertTrue(repositorio().buscar("5579999999999").isEmpty());
    }

    @Test
    void replacesPreviousSessionData() {
        SessaoConversa primeira = new SessaoConversa(ANA);
        primeira.adicionarParticipacao(new ParticipacaoEmMediacao(
                "med-1", PapelNaMediacao.CITADO, "Barulho"));
        primeira.adicionarParticipacao(new ParticipacaoEmMediacao(
                "med-2", PapelNaMediacao.RECLAMANTE, "Garagem"));
        primeira.registrarDado("descricao", "Antiga");
        repositorio().salvar(primeira);

        SessaoConversa segunda = new SessaoConversa(ANA);
        segunda.adicionarParticipacao(new ParticipacaoEmMediacao(
                "med-2", PapelNaMediacao.RECLAMANTE, "Garagem"));
        segunda.registrarDado("unidade", "A-101");
        repositorio().salvar(segunda);

        SessaoConversa recuperada = repositorio().buscar(ANA.telefone()).orElseThrow();

        assertEquals(List.of(new ParticipacaoEmMediacao(
                "med-2", PapelNaMediacao.RECLAMANTE, "Garagem")), recuperada.mediacoes());
        assertEquals(Map.of("unidade", "A-101"), recuperada.dadosColetados());
        assertTrue(recuperada.mediacaoAtiva().isEmpty());
    }

    @Test
    void motorContinuesConversationAfterRestartWithJdbcRepository() {
        List<String> recebidas = new ArrayList<>();
        FluxoDeReclamacao fluxo = new FluxoDeReclamacao() {
            @Override
            public RespostaConversa iniciar(SessaoConversa sessao) {
                return new RespostaConversa(Destino.ABERTURA_DE_RECLAMACAO, "Qual é o problema?");
            }

            @Override
            public RespostaConversa receber(SessaoConversa sessao, String texto) {
                recebidas.add(texto);
                return RespostaConversa.semTexto(Destino.ABERTURA_DE_RECLAMACAO);
            }
        };
        MotorDaConversa primeiro = motor(repositorio(), fluxo);
        primeiro.receber(ANA.telefone(), "oi");
        primeiro.receber(ANA.telefone(), "1");

        MotorDaConversa depoisDoReinicio = motor(repositorio(), fluxo);
        RespostaConversa resposta = depoisDoReinicio.receber(ANA.telefone(), "Barulho");

        assertEquals(Destino.ABERTURA_DE_RECLAMACAO, resposta.destino());
        assertEquals(List.of("Barulho"), recebidas);
    }

    private MotorDaConversa motor(RepositorioDeSessoes repositorio, FluxoDeReclamacao fluxo) {
        DiretorioDeMoradores diretorio = telefone ->
                Optional.of(ANA).filter(morador -> morador.telefone().equals(telefone));
        Mediador mediador = mensagem -> {
        };
        return new MotorDaConversa(diretorio, repositorio, mediador, fluxo,
                Clock.fixed(AGORA, ZoneOffset.UTC), Duration.ofMinutes(30));
    }

    private RepositorioDeSessoesJdbc repositorio() {
        return new RepositorioDeSessoesJdbc(dataSource());
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void criarTabelas() {
        try (Connection conexao = dataSource().getConnection();
             Statement comando = conexao.createStatement()) {
            comando.execute("create table sessoes_conversa ("
                    + " telefone text primary key,"
                    + " morador_id text not null,"
                    + " morador_nome text not null,"
                    + " etapa text not null,"
                    + " mediacao_ativa_id text,"
                    + " mensagem_pendente text,"
                    + " ultima_mensagem text)");
            comando.execute("create table sessoes_participacoes ("
                    + " telefone text not null references sessoes_conversa (telefone) on delete cascade,"
                    + " ordem integer not null,"
                    + " mediacao_id text not null,"
                    + " papel text not null,"
                    + " resumo text not null,"
                    + " primary key (telefone, mediacao_id))");
            comando.execute("create table sessoes_dados ("
                    + " telefone text not null references sessoes_conversa (telefone) on delete cascade,"
                    + " chave text not null,"
                    + " valor text,"
                    + " primary key (telefone, chave))");
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar as tabelas", e);
        }
    }
}
