package br.com.kyoto.core.conversa;

/**
 * Abertura de reclamação (#24). Ao terminar ou cancelar, chama
 * {@link SessaoConversa#voltarAoMenu} ou {@link SessaoConversa#entrarNaMediacao}.
 */
public interface FluxoDeReclamacao {

    RespostaConversa iniciar(SessaoConversa sessao);

    RespostaConversa receber(SessaoConversa sessao, String texto);
}
