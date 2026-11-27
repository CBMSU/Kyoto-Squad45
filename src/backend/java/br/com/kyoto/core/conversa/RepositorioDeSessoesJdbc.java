package br.com.kyoto.core.conversa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public class RepositorioDeSessoesJdbc implements RepositorioDeSessoes {

    private final DataSource dataSource;

    public RepositorioDeSessoesJdbc(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<SessaoConversa> buscar(String telefone) {
        Objects.requireNonNull(telefone, "telefone");
        try (Connection conexao = dataSource.getConnection()) {
            return buscar(conexao, telefone);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar a sessão", e);
        }
    }

    @Override
    public void salvar(SessaoConversa sessao) {
        Objects.requireNonNull(sessao, "sessao");
        try (Connection conexao = dataSource.getConnection()) {
            boolean autoCommit = conexao.getAutoCommit();
            conexao.setAutoCommit(false);
            try {
                salvar(conexao, sessao);
                conexao.commit();
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            } finally {
                conexao.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar a sessão", e);
        }
    }

    private Optional<SessaoConversa> buscar(Connection conexao, String telefone) throws SQLException {
        String sql = "select morador_id, morador_nome, etapa, mediacao_ativa_id, mensagem_pendente,"
                + " ultima_mensagem from sessoes_conversa where telefone = ?";
        String moradorId;
        String moradorNome;
        String etapa;
        String mediacaoAtivaId;
        String mensagemPendente;
        String ultimaMensagem;
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, telefone);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }
                moradorId = resultado.getString("morador_id");
                moradorNome = resultado.getString("morador_nome");
                etapa = resultado.getString("etapa");
                mediacaoAtivaId = resultado.getString("mediacao_ativa_id");
                mensagemPendente = resultado.getString("mensagem_pendente");
                ultimaMensagem = resultado.getString("ultima_mensagem");
            }
        }

        MoradorDaConversa morador = new MoradorDaConversa(moradorId, moradorNome, telefone);
        return Optional.of(SessaoConversa.restore(
                morador,
                EtapaConversa.valueOf(etapa),
                buscarParticipacoes(conexao, telefone),
                mediacaoAtivaId,
                buscarDados(conexao, telefone),
                mensagemPendente,
                ultimaMensagem == null ? null : Instant.parse(ultimaMensagem)));
    }

    private List<ParticipacaoEmMediacao> buscarParticipacoes(Connection conexao, String telefone)
            throws SQLException {
        String sql = "select mediacao_id, papel, resumo from sessoes_participacoes"
                + " where telefone = ? order by ordem";
        List<ParticipacaoEmMediacao> participacoes = new ArrayList<>();
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, telefone);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    participacoes.add(new ParticipacaoEmMediacao(
                            resultado.getString("mediacao_id"),
                            PapelNaMediacao.valueOf(resultado.getString("papel")),
                            resultado.getString("resumo")));
                }
            }
        }
        return participacoes;
    }

    private Map<String, String> buscarDados(Connection conexao, String telefone) throws SQLException {
        String sql = "select chave, valor from sessoes_dados where telefone = ?";
        Map<String, String> dados = new LinkedHashMap<>();
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, telefone);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    dados.put(resultado.getString("chave"), resultado.getString("valor"));
                }
            }
        }
        return dados;
    }

    private void salvar(Connection conexao, SessaoConversa sessao) throws SQLException {
        String telefone = sessao.morador().telefone();
        apagar(conexao, "delete from sessoes_participacoes where telefone = ?", telefone);
        apagar(conexao, "delete from sessoes_dados where telefone = ?", telefone);
        apagar(conexao, "delete from sessoes_conversa where telefone = ?", telefone);

        String sql = "insert into sessoes_conversa (telefone, morador_id, morador_nome, etapa,"
                + " mediacao_ativa_id, mensagem_pendente, ultima_mensagem)"
                + " values (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, telefone);
            comando.setString(2, sessao.morador().id());
            comando.setString(3, sessao.morador().nome());
            comando.setString(4, sessao.etapa().name());
            comando.setString(5, sessao.mediacaoAtiva()
                    .map(ParticipacaoEmMediacao::mediacaoId).orElse(null));
            comando.setString(6, sessao.mensagemPendente().orElse(null));
            comando.setString(7, sessao.ultimaMensagem().map(Instant::toString).orElse(null));
            comando.executeUpdate();
        }

        salvarParticipacoes(conexao, telefone, sessao.mediacoes());
        salvarDados(conexao, telefone, sessao.dadosColetados());
    }

    private void salvarParticipacoes(Connection conexao, String telefone,
                                     List<ParticipacaoEmMediacao> participacoes) throws SQLException {
        String sql = "insert into sessoes_participacoes (telefone, ordem, mediacao_id, papel, resumo)"
                + " values (?, ?, ?, ?, ?)";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            int ordem = 0;
            for (ParticipacaoEmMediacao participacao : participacoes) {
                comando.setString(1, telefone);
                comando.setInt(2, ordem++);
                comando.setString(3, participacao.mediacaoId());
                comando.setString(4, participacao.papel().name());
                comando.setString(5, participacao.resumo());
                comando.addBatch();
            }
            comando.executeBatch();
        }
    }

    private void salvarDados(Connection conexao, String telefone, Map<String, String> dados)
            throws SQLException {
        String sql = "insert into sessoes_dados (telefone, chave, valor) values (?, ?, ?)";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (Map.Entry<String, String> dado : dados.entrySet()) {
                comando.setString(1, telefone);
                comando.setString(2, dado.getKey());
                comando.setString(3, dado.getValue());
                comando.addBatch();
            }
            comando.executeBatch();
        }
    }

    private static void apagar(Connection conexao, String sql, String telefone) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, telefone);
            comando.executeUpdate();
        }
    }
}
