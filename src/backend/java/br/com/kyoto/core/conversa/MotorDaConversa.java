package br.com.kyoto.core.conversa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Decide para onde vai cada mensagem do morador. O síndico não é um destino do menu:
 * ele é acionado dentro da mediação, quando não há acordo (#26). Não conhece o WhatsApp: recebe
 * telefone (já normalizado) e texto, e devolve o que responder.
 */
public class MotorDaConversa {

    public static final Duration TEMPO_LIMITE_PADRAO = Duration.ofMinutes(30);

    private final DiretorioDeMoradores diretorio;
    private final RepositorioDeSessoes sessoes;
    private final Mediador mediador;
    private final FluxoDeReclamacao fluxoDeReclamacao;
    private final Clock relogio;
    private final Duration tempoLimite;

    public MotorDaConversa(DiretorioDeMoradores diretorio, RepositorioDeSessoes sessoes,
                           Mediador mediador, FluxoDeReclamacao fluxoDeReclamacao) {
        this(diretorio, sessoes, mediador, fluxoDeReclamacao, Clock.systemUTC(), TEMPO_LIMITE_PADRAO);
    }

    public MotorDaConversa(DiretorioDeMoradores diretorio, RepositorioDeSessoes sessoes,
                           Mediador mediador, FluxoDeReclamacao fluxoDeReclamacao,
                           Clock relogio, Duration tempoLimite) {
        this.diretorio = Objects.requireNonNull(diretorio, "diretorio");
        this.sessoes = Objects.requireNonNull(sessoes, "sessoes");
        this.mediador = Objects.requireNonNull(mediador, "mediador");
        this.fluxoDeReclamacao = Objects.requireNonNull(fluxoDeReclamacao, "fluxoDeReclamacao");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.tempoLimite = Objects.requireNonNull(tempoLimite, "tempoLimite");
    }

    public RespostaConversa receber(String telefone, String texto) {
        Instant agora = relogio.instant();
        Optional<SessaoConversa> existente = sessoes.buscar(telefone);

        SessaoConversa sessao;
        RespostaConversa resposta;
        if (existente.isPresent()) {
            sessao = existente.get();
            if (sessao.expirou(agora, tempoLimite)) {
                sessao.voltarAoMenu();
                resposta = boasVindas(sessao);
            } else {
                resposta = tratar(sessao, texto == null ? "" : texto);
            }
        } else {
            Optional<MoradorDaConversa> morador = diretorio.buscarPorTelefone(telefone);
            if (morador.isEmpty()) {
                return new RespostaConversa(Destino.SEM_CADASTRO, Mensagens.SEM_CADASTRO);
            }
            sessao = new SessaoConversa(morador.get());
            resposta = boasVindas(sessao);
        }

        sessao.registrarMensagem(agora);
        sessoes.salvar(sessao);
        return resposta;
    }

    /** Usado pela #24. Cria a sessão se o morador citado nunca falou com o número. */
    public void convidarParaMediacao(MoradorDaConversa morador, ParticipacaoEmMediacao participacao) {
        SessaoConversa sessao = sessoes.buscar(morador.telefone())
                .orElseGet(() -> new SessaoConversa(morador));
        if (sessao.expirou(relogio.instant(), tempoLimite)) {
            sessao.voltarAoMenu();
        }
        sessao.receberConvite(participacao);
        sessoes.salvar(sessao);
    }

    private RespostaConversa tratar(SessaoConversa sessao, String texto) {
        String comando = texto.strip().toLowerCase(Locale.ROOT);
        if (comando.equals("0") || comando.equals("menu")) {
            sessao.voltarAoMenu();
            return new RespostaConversa(Destino.MENU, Mensagens.MENU);
        }

        return switch (sessao.etapa()) {
            case MENU -> tratarMenu(sessao, comando);
            case ABRINDO_RECLAMACAO -> fluxoDeReclamacao.receber(sessao, texto);
            case CONVIDADO_PARA_MEDIACAO, EM_MEDIACAO -> encaminharParaMediacao(sessao, texto);
            case ESCOLHENDO_MEDIACAO -> tratarEscolhaDeMediacao(sessao, texto, comando);
        };
    }

    private RespostaConversa tratarMenu(SessaoConversa sessao, String comando) {
        return switch (comando) {
            case "1" -> {
                sessao.iniciarAberturaDeReclamacao();
                yield fluxoDeReclamacao.iniciar(sessao);
            }
            case "2" -> mostrarMediacoes(sessao);
            default -> new RespostaConversa(Destino.MENU, Mensagens.comMenu(Mensagens.OPCAO_INVALIDA));
        };
    }

    private RespostaConversa mostrarMediacoes(SessaoConversa sessao) {
        List<ParticipacaoEmMediacao> mediacoes = sessao.mediacoes();
        if (mediacoes.isEmpty()) {
            return new RespostaConversa(Destino.MENU, Mensagens.comMenu(Mensagens.SEM_MEDIACOES));
        }
        if (mediacoes.size() == 1) {
            ParticipacaoEmMediacao unica = mediacoes.get(0);
            sessao.entrarNaMediacao(unica);
            return new RespostaConversa(Destino.MEDIACAO, Mensagens.casoEscolhido(unica));
        }
        sessao.escolherMediacao();
        return new RespostaConversa(Destino.ESCOLHA_DE_MEDIACAO, Mensagens.escolhaDeMediacao(mediacoes));
    }

    private RespostaConversa encaminharParaMediacao(SessaoConversa sessao, String texto) {
        ParticipacaoEmMediacao ativa = sessao.mediacaoAtiva()
                .orElseThrow(() -> new IllegalStateException("Sessão em mediação sem mediação ativa"));
        sessao.entrarNaMediacao(ativa);
        mediador.encaminhar(new MensagemDeMediacao(
                ativa.mediacaoId(), ativa.papel(), sessao.morador().telefone(), texto));
        return RespostaConversa.semTexto(Destino.MEDIACAO);
    }

    private RespostaConversa tratarEscolhaDeMediacao(SessaoConversa sessao, String texto, String comando) {
        List<ParticipacaoEmMediacao> mediacoes = sessao.mediacoes();
        Optional<Integer> numero = numeroDaOpcao(comando, mediacoes.size());
        if (numero.isEmpty()) {
            sessao.guardarMensagemPendente(texto);
            return new RespostaConversa(Destino.ESCOLHA_DE_MEDIACAO, Mensagens.escolhaDeMediacao(mediacoes));
        }

        ParticipacaoEmMediacao escolhida = mediacoes.get(numero.get() - 1);
        sessao.entrarNaMediacao(escolhida);
        Optional<String> pendente = sessao.retirarMensagemPendente();
        if (pendente.isEmpty()) {
            return new RespostaConversa(Destino.MEDIACAO, Mensagens.casoEscolhido(escolhida));
        }
        mediador.encaminhar(new MensagemDeMediacao(
                escolhida.mediacaoId(), escolhida.papel(), sessao.morador().telefone(), pendente.get()));
        return new RespostaConversa(Destino.MEDIACAO, Mensagens.pendenteEncaminhada(escolhida));
    }

    private static Optional<Integer> numeroDaOpcao(String comando, int totalDeOpcoes) {
        try {
            int numero = Integer.parseInt(comando);
            return numero >= 1 && numero <= totalDeOpcoes ? Optional.of(numero) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static RespostaConversa boasVindas(SessaoConversa sessao) {
        return new RespostaConversa(Destino.MENU, Mensagens.boasVindas(sessao.morador().nome()));
    }
}
