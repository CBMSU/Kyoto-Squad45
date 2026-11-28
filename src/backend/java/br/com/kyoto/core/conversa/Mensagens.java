package br.com.kyoto.core.conversa;

import java.util.List;

final class Mensagens {

    static final String MENU = """
            Escolha uma opção:
            1 - Abrir reclamação sobre outro morador
            2 - Minhas mediações

            Digite 0 ou "menu" para voltar a este menu a qualquer momento.""";

    static final String SEM_CADASTRO = "Não encontramos este número no cadastro do condomínio. "
            + "Procure a administração para atualizar os seus dados.";

    static final String OPCAO_INVALIDA = "Não entendi a opção.";

    static final String SEM_MEDIACOES = "Você não tem mediações em andamento.";

    private Mensagens() {
    }

    static String boasVindas(String nome) {
        return "Olá, " + nome + "! Eu sou o assistente do condomínio.\n\n" + MENU;
    }

    static String comMenu(String aviso) {
        return aviso + "\n\n" + MENU;
    }

    static String escolhaDeMediacao(List<ParticipacaoEmMediacao> mediacoes) {
        StringBuilder texto = new StringBuilder("Você participa de mais de um caso. ")
                .append("A qual deles sua mensagem se refere?\n");
        for (int i = 0; i < mediacoes.size(); i++) {
            texto.append(i + 1).append(" - ").append(mediacoes.get(i).resumo()).append('\n');
        }
        return texto.append("Responda com o número do caso.").toString();
    }

    static String casoEscolhido(ParticipacaoEmMediacao participacao) {
        return "Você está no caso: " + participacao.resumo() + ". Pode escrever sua mensagem.";
    }

    static String pendenteEncaminhada(ParticipacaoEmMediacao participacao) {
        return "Sua mensagem foi encaminhada no caso: " + participacao.resumo() + ".";
    }
}
