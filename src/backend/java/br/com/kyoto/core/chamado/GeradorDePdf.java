package br.com.kyoto.core.chamado;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GeradorDePdf {

    private static final Charset WIN_1252 = Charset.forName("Windows-1252");
    private static final int CARACTERES_POR_LINHA = 92;
    private static final int LINHAS_POR_PAGINA = 48;
    private static final int TAMANHO_FONTE = 11;
    private static final int ENTRE_LINHAS = 14;
    private static final int MARGEM_ESQUERDA = 50;
    private static final int TOPO = 790;

    private GeradorDePdf() {
    }

    static byte[] gerar(String texto) {
        Objects.requireNonNull(texto, "texto");
        List<String> linhas = quebrarEmLinhas(texto);
        List<List<String>> paginas = paginar(linhas);
        return montarPdf(paginas);
    }

    private static List<String> quebrarEmLinhas(String texto) {
        List<String> resultado = new ArrayList<>();
        for (String paragrafo : texto.split("\n", -1)) {
            if (paragrafo.isEmpty()) {
                resultado.add("");
                continue;
            }
            int i = 0;
            while (i < paragrafo.length()) {
                int fim = Math.min(i + CARACTERES_POR_LINHA, paragrafo.length());
                if (fim < paragrafo.length()) {
                    int espaco = paragrafo.lastIndexOf(' ', fim);
                    if (espaco > i) {
                        fim = espaco;
                    }
                }
                resultado.add(paragrafo.substring(i, fim).stripTrailing());
                i = fim;
                while (i < paragrafo.length() && paragrafo.charAt(i) == ' ') {
                    i++;
                }
            }
        }
        return resultado;
    }

    private static List<List<String>> paginar(List<String> linhas) {
        List<List<String>> paginas = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        for (String linha : linhas) {
            if (atual.size() >= LINHAS_POR_PAGINA) {
                paginas.add(atual);
                atual = new ArrayList<>();
            }
            atual.add(linha);
        }
        if (atual.isEmpty() && paginas.isEmpty()) {
            atual.add("");
        }
        if (!atual.isEmpty()) {
            paginas.add(atual);
        }
        return paginas;
    }

    private static byte[] montarPdf(List<List<String>> paginas) {
        int totalDePaginas = paginas.size();
        int totalDeObjetos = 3 + (totalDePaginas * 2);

        List<byte[]> objetos = new ArrayList<>();
        objetos.add(null); // objeto 0 não existe
        objetos.add(montarCatalogo(totalDePaginas));
        objetos.add(montarPaginas(totalDePaginas));
        objetos.add(montarFonte());

        List<byte[]> conteudos = new ArrayList<>();
        for (List<String> pagina : paginas) {
            conteudos.add(montarConteudo(pagina));
        }
        for (int i = 0; i < totalDePaginas; i++) {
            int numeroDaPagina = 4 + (i * 2);
            int numeroDoConteudo = numeroDaPagina + 1;
            objetos.add(montarPagina(numeroDoConteudo));
            objetos.add(conteudos.get(i));
        }

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        escrever(saida, "%PDF-1.4\n");

        int[] deslocamentos = new int[totalDeObjetos + 1];
        for (int i = 1; i <= totalDeObjetos; i++) {
            deslocamentos[i] = saida.size();
            escrever(saida, i + " 0 obj\n");
            saida.write(objetos.get(i), 0, objetos.get(i).length);
            escrever(saida, "\nendobj\n");
        }

        int inicioXref = saida.size();
        escrever(saida, "xref\n");
        escrever(saida, "0 " + (totalDeObjetos + 1) + "\n");
        escrever(saida, "0000000000 65535 f \n");
        for (int i = 1; i <= totalDeObjetos; i++) {
            escrever(saida, String.format("%010d 00000 n \n", deslocamentos[i]));
        }
        escrever(saida, "trailer\n");
        escrever(saida, "<< /Size " + (totalDeObjetos + 1) + " /Root 1 0 R >>\n");
        escrever(saida, "startxref\n");
        escrever(saida, inicioXref + "\n");
        escrever(saida, "%%EOF\n");

        return saida.toByteArray();
    }

    private static byte[] montarCatalogo(int totalDePaginas) {
        StringBuilder sb = new StringBuilder();
        sb.append("<< /Type /Catalog /Pages 2 0 R >>");
        return ascii(sb.toString());
    }

    private static byte[] montarPaginas(int totalDePaginas) {
        StringBuilder sb = new StringBuilder();
        sb.append("<< /Type /Pages /Kids [");
        for (int i = 0; i < totalDePaginas; i++) {
            int numeroDaPagina = 4 + (i * 2);
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(numeroDaPagina).append(" 0 R");
        }
        sb.append("] /Count ").append(totalDePaginas).append(" >>");
        return ascii(sb.toString());
    }

    private static byte[] montarFonte() {
        return ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
    }

    private static byte[] montarPagina(int numeroDoConteudo) {
        return ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + numeroDoConteudo + " 0 R >>");
    }

    private static byte[] montarConteudo(List<String> pagina) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        escrever(saida, "BT\n");
        escrever(saida, "/F1 " + TAMANHO_FONTE + " Tf\n");
        escrever(saida, ENTRE_LINHAS + " TL\n");
        escrever(saida, MARGEM_ESQUERDA + " " + TOPO + " Td\n");
        for (String linha : pagina) {
            escrever(saida, "(" + escapar(linha) + ") Tj\n");
            escrever(saida, "T*\n");
        }
        escrever(saida, "ET\n");
        byte[] corpo = saida.toByteArray();
        ByteArrayOutputStream objeto = new ByteArrayOutputStream();
        escrever(objeto, "<< /Length " + corpo.length + " >>\nstream\n");
        objeto.write(corpo, 0, corpo.length);
        escrever(objeto, "endstream");
        return objeto.toByteArray();
    }

    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '(') {
                sb.append("\\(");
            } else if (c == ')') {
                sb.append("\\)");
            } else if (c == '\r' || c == '\n') {
                sb.append(" ");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static void escrever(ByteArrayOutputStream saida, String texto) {
        saida.write(texto.getBytes(WIN_1252), 0, texto.getBytes(WIN_1252).length);
    }

    private static void escrever(ByteArrayOutputStream saida, byte[] dados) {
        saida.write(dados, 0, dados.length);
    }
}
