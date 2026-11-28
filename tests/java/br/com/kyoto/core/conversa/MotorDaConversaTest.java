package br.com.kyoto.core.conversa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MotorDaConversaTest {

    private static final MoradorDaConversa ANA = new MoradorDaConversa("m1", "Ana", "5579999990001");
    private static final MoradorDaConversa BRUNO = new MoradorDaConversa("m2", "Bruno", "5579999990002");
    private static final String SEM_CADASTRO = "5579999999999";

    private static final ParticipacaoEmMediacao BARULHO_CITADO =
            new ParticipacaoEmMediacao("med-1", PapelNaMediacao.CITADO, "Barulho à noite");
    private static final ParticipacaoEmMediacao GARAGEM_CITADO =
            new ParticipacaoEmMediacao("med-2", PapelNaMediacao.CITADO, "Vaga de garagem");

    private RelogioAjustavel relogio;
    private RepositorioDeSessoes repositorio;
    private MediadorSimulado mediador;
    private FluxoDeReclamacaoSimulado fluxo;
    private MotorDaConversa motor;

    @BeforeEach
    void preparar() {
        relogio = new RelogioAjustavel(Instant.parse("2026-11-06T12:00:00Z"));
        repositorio = new RepositorioDeSessoesEmMemoria();
        mediador = new MediadorSimulado();
        fluxo = new FluxoDeReclamacaoSimulado();
        motor = novoMotor();
    }

    private MotorDaConversa novoMotor() {
        DiretorioDeMoradores diretorio = telefone ->
                Optional.ofNullable(Map.of(ANA.telefone(), ANA, BRUNO.telefone(), BRUNO).get(telefone));
        return new MotorDaConversa(diretorio, repositorio, mediador, fluxo,
                relogio, MotorDaConversa.TEMPO_LIMITE_PADRAO);
    }

    private EtapaConversa etapaDe(MoradorDaConversa morador) {
        return repositorio.buscar(morador.telefone()).orElseThrow().etapa();
    }

    @Test
    void moradorCadastradoRecebeSaudacaoComNomeEMenu() {
        RespostaConversa resposta = motor.receber(ANA.telefone(), "oi");

        assertEquals(Destino.MENU, resposta.destino());
        assertTrue(resposta.texto().contains("Olá, Ana!"));
        assertTrue(resposta.texto().contains("1 - Abrir reclamação"));
        assertEquals(EtapaConversa.MENU, etapaDe(ANA));
    }

    @Test
    void numeroNaoCadastradoRecebeOrientacaoESemSessao() {
        RespostaConversa resposta = motor.receber(SEM_CADASTRO, "oi");

        assertEquals(Destino.SEM_CADASTRO, resposta.destino());
        assertTrue(resposta.texto().contains("administração"));
        assertFalse(resposta.texto().contains("Ana"));
        assertTrue(repositorio.buscar(SEM_CADASTRO).isEmpty());
    }

    @Test
    void conversaContinuaDaEtapaCertaEntreMensagens() {
        motor.receber(ANA.telefone(), "oi");

        RespostaConversa inicio = motor.receber(ANA.telefone(), "1");
        RespostaConversa resposta = motor.receber(ANA.telefone(), "Barulho depois das 22h");

        assertEquals(Destino.ABERTURA_DE_RECLAMACAO, inicio.destino());
        assertEquals(Destino.ABERTURA_DE_RECLAMACAO, resposta.destino());
        assertEquals(EtapaConversa.ABRINDO_RECLAMACAO, etapaDe(ANA));
        assertEquals(List.of("Barulho depois das 22h"), fluxo.recebidas);
    }

    @Test
    void conversaContinuaComOutraInstanciaDoMotorUsandoOMesmoRepositorio() {
        motor.receber(ANA.telefone(), "oi");
        motor.receber(ANA.telefone(), "1");

        MotorDaConversa depoisDeReiniciar = novoMotor();
        RespostaConversa resposta = depoisDeReiniciar.receber(ANA.telefone(), "Vazamento");

        assertEquals(Destino.ABERTURA_DE_RECLAMACAO, resposta.destino());
        assertEquals(List.of("Vazamento"), fluxo.recebidas);
    }

    @Test
    void mensagensDoReclamanteChegamASophiaComMediacaoEPapel() {
        motor.receber(ANA.telefone(), "oi");
        motor.receber(ANA.telefone(), "1");
        motor.receber(ANA.telefone(), FluxoDeReclamacaoSimulado.CONFIRMAR);

        RespostaConversa resposta = motor.receber(ANA.telefone(), "O barulho continua");

        assertEquals(Destino.MEDIACAO, resposta.destino());
        assertFalse(resposta.temTexto());
        assertEquals(List.of(new MensagemDeMediacao(
                "med-1", PapelNaMediacao.RECLAMANTE, ANA.telefone(), "O barulho continua")), mediador.recebidas);
    }

    @Test
    void citadoQueNuncaFalouComONumeroEntraNaMediacaoAoResponder() {
        motor.convidarParaMediacao(BRUNO, BARULHO_CITADO);
        assertEquals(EtapaConversa.CONVIDADO_PARA_MEDIACAO, etapaDe(BRUNO));

        RespostaConversa resposta = motor.receber(BRUNO.telefone(), "Não sabia, vou diminuir o som");

        assertEquals(Destino.MEDIACAO, resposta.destino());
        assertEquals(EtapaConversa.EM_MEDIACAO, etapaDe(BRUNO));
        assertEquals(List.of(new MensagemDeMediacao(
                "med-1", PapelNaMediacao.CITADO, BRUNO.telefone(), "Não sabia, vou diminuir o som")),
                mediador.recebidas);
    }

    @Test
    void conviteSemRespostaNaoExpira() {
        motor.convidarParaMediacao(BRUNO, BARULHO_CITADO);
        relogio.avancar(Duration.ofHours(5));

        RespostaConversa resposta = motor.receber(BRUNO.telefone(), "Recebi o aviso");

        assertEquals(Destino.MEDIACAO, resposta.destino());
        assertEquals(1, mediador.recebidas.size());
    }

    @Test
    void moradorEmDuasMediacoesEscolheAQualCasoAMensagemSeRefere() {
        motor.convidarParaMediacao(BRUNO, BARULHO_CITADO);
        motor.receber(BRUNO.telefone(), "Primeira resposta");
        motor.convidarParaMediacao(BRUNO, GARAGEM_CITADO);

        RespostaConversa pergunta = motor.receber(BRUNO.telefone(), "A vaga é minha");

        assertEquals(Destino.ESCOLHA_DE_MEDIACAO, pergunta.destino());
        assertTrue(pergunta.texto().contains("1 - Barulho à noite"));
        assertTrue(pergunta.texto().contains("2 - Vaga de garagem"));
        assertEquals(1, mediador.recebidas.size());

        RespostaConversa escolha = motor.receber(BRUNO.telefone(), "2");

        assertEquals(Destino.MEDIACAO, escolha.destino());
        assertEquals(new MensagemDeMediacao(
                "med-2", PapelNaMediacao.CITADO, BRUNO.telefone(), "A vaga é minha"), mediador.ultima());

        motor.receber(BRUNO.telefone(), "Tenho o contrato");
        assertEquals("med-2", mediador.ultima().mediacaoId());
    }

    @Test
    void minhasMediacoesComUmCasoEntraDireto() {
        motor.convidarParaMediacao(BRUNO, BARULHO_CITADO);
        motor.receber(BRUNO.telefone(), "menu");

        RespostaConversa resposta = motor.receber(BRUNO.telefone(), "2");

        assertEquals(Destino.MEDIACAO, resposta.destino());
        assertTrue(resposta.texto().contains("Barulho à noite"));
        assertEquals(EtapaConversa.EM_MEDIACAO, etapaDe(BRUNO));
    }

    @Test
    void minhasMediacoesSemCasosVoltaAoMenu() {
        motor.receber(ANA.telefone(), "oi");

        RespostaConversa resposta = motor.receber(ANA.telefone(), "2");

        assertEquals(Destino.MENU, resposta.destino());
        assertTrue(resposta.texto().contains("não tem mediações"));
    }

    @Test
    void sessaoInativaVoltaAoInicioSemPerderAMediacao() {
        motor.convidarParaMediacao(BRUNO, BARULHO_CITADO);
        motor.receber(BRUNO.telefone(), "Primeira resposta");
        relogio.avancar(Duration.ofMinutes(31));

        RespostaConversa resposta = motor.receber(BRUNO.telefone(), "Voltei");

        assertEquals(Destino.MENU, resposta.destino());
        assertTrue(resposta.texto().contains("Olá, Bruno!"));
        assertEquals(1, mediador.recebidas.size());

        RespostaConversa retomada = motor.receber(BRUNO.telefone(), "2");
        assertEquals(Destino.MEDIACAO, retomada.destino());
        assertEquals("med-1", repositorio.buscar(BRUNO.telefone()).orElseThrow()
                .mediacaoAtiva().orElseThrow().mediacaoId());
    }

    @Test
    void sessaoAtivaHaMenosDoLimiteNaoExpira() {
        motor.receber(ANA.telefone(), "oi");
        motor.receber(ANA.telefone(), "1");
        relogio.avancar(Duration.ofMinutes(29));

        RespostaConversa resposta = motor.receber(ANA.telefone(), "Barulho");

        assertEquals(Destino.ABERTURA_DE_RECLAMACAO, resposta.destino());
    }

    @Test
    void zeroOuMenuVoltaAoMenuEDescartaOsDadosDaReclamacao() {
        motor.receber(ANA.telefone(), "oi");
        motor.receber(ANA.telefone(), "1");
        motor.receber(ANA.telefone(), "Barulho");

        RespostaConversa resposta = motor.receber(ANA.telefone(), "  MENU ");

        assertEquals(Destino.MENU, resposta.destino());
        SessaoConversa sessao = repositorio.buscar(ANA.telefone()).orElseThrow();
        assertEquals(EtapaConversa.MENU, sessao.etapa());
        assertTrue(sessao.dadosColetados().isEmpty());

        motor.receber(ANA.telefone(), "1");
        assertEquals(Destino.MENU, motor.receber(ANA.telefone(), "0").destino());
    }

    @Test
    void opcaoInvalidaNoMenuRepeteOMenu() {
        motor.receber(ANA.telefone(), "oi");

        for (String opcao : List.of("quero falar", "3")) {
            RespostaConversa resposta = motor.receber(ANA.telefone(), opcao);

            assertEquals(Destino.MENU, resposta.destino());
            assertTrue(resposta.texto().contains("Não entendi"));
            assertTrue(resposta.texto().contains("Escolha uma opção"));
        }
        assertEquals(EtapaConversa.MENU, etapaDe(ANA));
    }

    @Test
    void conviteDuranteAberturaDeReclamacaoNaoInterrompeOFluxo() {
        motor.receber(ANA.telefone(), "oi");
        motor.receber(ANA.telefone(), "1");

        motor.convidarParaMediacao(ANA, GARAGEM_CITADO);

        assertEquals(EtapaConversa.ABRINDO_RECLAMACAO, etapaDe(ANA));
        assertEquals(List.of(GARAGEM_CITADO), repositorio.buscar(ANA.telefone()).orElseThrow().mediacoes());
    }

    private static final class MediadorSimulado implements Mediador {
        private final List<MensagemDeMediacao> recebidas = new ArrayList<>();

        @Override
        public void encaminhar(MensagemDeMediacao mensagem) {
            recebidas.add(mensagem);
        }

        MensagemDeMediacao ultima() {
            return recebidas.get(recebidas.size() - 1);
        }
    }

    /** CONFIRMAR coloca o reclamante na mediação. */
    private static final class FluxoDeReclamacaoSimulado implements FluxoDeReclamacao {
        static final String CONFIRMAR = "confirmar";
        private final List<String> recebidas = new ArrayList<>();

        @Override
        public RespostaConversa iniciar(SessaoConversa sessao) {
            return new RespostaConversa(Destino.ABERTURA_DE_RECLAMACAO, "Qual é o problema?");
        }

        @Override
        public RespostaConversa receber(SessaoConversa sessao, String texto) {
            if (texto.equals(CONFIRMAR)) {
                sessao.entrarNaMediacao(
                        new ParticipacaoEmMediacao("med-1", PapelNaMediacao.RECLAMANTE, "Barulho à noite"));
                return new RespostaConversa(Destino.MEDIACAO, "Reclamação registrada.");
            }
            recebidas.add(texto);
            sessao.registrarDado("descricao", texto);
            return RespostaConversa.semTexto(Destino.ABERTURA_DE_RECLAMACAO);
        }
    }

    private static final class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
