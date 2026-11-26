package br.com.kyoto.core.conversa;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Estado da conversa de um morador. Voltar ao menu não encerra mediações:
 * as participações continuam guardadas.
 */
public class SessaoConversa {
    private final MoradorDaConversa morador;
    private final List<ParticipacaoEmMediacao> mediacoes = new ArrayList<>();
    private final Map<String, String> dadosColetados = new LinkedHashMap<>();
    private EtapaConversa etapa = EtapaConversa.MENU;
    private ParticipacaoEmMediacao mediacaoAtiva;
    private String mensagemPendente;
    private Instant ultimaMensagem;

    public SessaoConversa(MoradorDaConversa morador) {
        this.morador = Objects.requireNonNull(morador, "morador");
    }

    public MoradorDaConversa morador() {
        return morador;
    }

    public EtapaConversa etapa() {
        return etapa;
    }

    public List<ParticipacaoEmMediacao> mediacoes() {
        return Collections.unmodifiableList(mediacoes);
    }

    public Optional<ParticipacaoEmMediacao> mediacaoAtiva() {
        return Optional.ofNullable(mediacaoAtiva);
    }

    public Map<String, String> dadosColetados() {
        return Collections.unmodifiableMap(dadosColetados);
    }

    public Optional<Instant> ultimaMensagem() {
        return Optional.ofNullable(ultimaMensagem);
    }

    public void registrarDado(String chave, String valor) {
        dadosColetados.put(chave, valor);
    }

    public void voltarAoMenu() {
        etapa = EtapaConversa.MENU;
        mediacaoAtiva = null;
        mensagemPendente = null;
        dadosColetados.clear();
    }

    public void adicionarParticipacao(ParticipacaoEmMediacao participacao) {
        Objects.requireNonNull(participacao, "participacao");
        boolean jaExiste = mediacoes.stream()
                .anyMatch(m -> m.mediacaoId().equals(participacao.mediacaoId()));
        if (!jaExiste) {
            mediacoes.add(participacao);
        }
    }

    public void entrarNaMediacao(ParticipacaoEmMediacao participacao) {
        adicionarParticipacao(participacao);
        mediacaoAtiva = participacao;
        etapa = EtapaConversa.EM_MEDIACAO;
    }

    void iniciarAberturaDeReclamacao() {
        dadosColetados.clear();
        etapa = EtapaConversa.ABRINDO_RECLAMACAO;
    }

    void escolherMediacao() {
        mediacaoAtiva = null;
        etapa = EtapaConversa.ESCOLHENDO_MEDIACAO;
    }

    /**
     * No menu, passa a aguardar a resposta ao convite. Em outra mediação, o morador
     * vai precisar escolher o caso. Abrindo uma reclamação, o fluxo não é interrompido.
     */
    void receberConvite(ParticipacaoEmMediacao participacao) {
        adicionarParticipacao(participacao);
        switch (etapa) {
            case MENU -> {
                mediacaoAtiva = participacao;
                etapa = EtapaConversa.CONVIDADO_PARA_MEDIACAO;
            }
            case CONVIDADO_PARA_MEDIACAO, EM_MEDIACAO -> {
                boolean mesmaMediacao = mediacaoAtiva != null
                        && mediacaoAtiva.mediacaoId().equals(participacao.mediacaoId());
                if (!mesmaMediacao) {
                    escolherMediacao();
                }
            }
            case ESCOLHENDO_MEDIACAO, ABRINDO_RECLAMACAO -> {
            }
        }
    }

    void guardarMensagemPendente(String texto) {
        mensagemPendente = mensagemPendente == null ? texto : mensagemPendente + "\n" + texto;
    }

    Optional<String> retirarMensagemPendente() {
        Optional<String> pendente = Optional.ofNullable(mensagemPendente);
        mensagemPendente = null;
        return pendente;
    }

    void registrarMensagem(Instant quando) {
        ultimaMensagem = quando;
    }

    /** Convite não respondido não expira aqui: o prazo de resposta é da mediação (#26). */
    boolean expirou(Instant agora, Duration tempoLimite) {
        if (ultimaMensagem == null || etapa == EtapaConversa.CONVIDADO_PARA_MEDIACAO) {
            return false;
        }
        return ultimaMensagem.plus(tempoLimite).isBefore(agora);
    }
}
