# WhatsApp oficial + SophIA — Java

A integração roda como um serviço Java 17 ou superior, usando somente o JDK.
Não requer Node.js, Deno, Maven ou dependências externas. O webhook é
`GET/POST /webhooks/whatsapp`. O Supabase é usado apenas para persistência via
REST/PostgreSQL; não executa código da integração.

## Executar

Na raiz do repositório, compile os arquivos Java:

```sh
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -d build/classes src/backend/java/br/com/kyoto/whatsapp/*.java
```

Copie `src/backend/.env.example` para `src/backend/.env` e preencha os valores.
O programa lê variáveis do ambiente; ele não carrega `.env` automaticamente.
Em um terminal POSIX, exporte o arquivo local que você preencheu:

```sh
set -a
. src/backend/.env
set +a
java -cp build/classes br.com.kyoto.whatsapp.Application
```

O servidor inicia na porta `8080` ou no valor de `PORT`. Mantenha segredos fora
do Git e do frontend. Em hospedagem, use o gerenciador de segredos do ambiente.

## Banco de dados

Execute `database/migrations/202610180001_whatsapp_inbox.sql` no SQL Editor do
projeto Supabase existente. Isso cria `whatsapp_inbox` e a função atômica
`claim_whatsapp_message`. Não reaplique se já executou essa migração: o esquema
é o mesmo da implementação anterior.

Configure `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` apenas no backend Java.
A tabela tem RLS e bloqueia clientes públicos. São armazenados somente IDs de
mensagens e estados, sem conteúdo das conversas.

## Configurar a Meta

1. Configure o produto WhatsApp em seu app empresarial na Meta e obtenha o
   ID do número, App Secret e access token. Comece com o número de teste.
   Para produção, configure token de usuário de sistema com acesso ao número
   e permissão `whatsapp_business_messaging`.
2. Preencha `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_APP_SECRET`,
   `WHATSAPP_ACCESS_TOKEN` e `WHATSAPP_GRAPH_VERSION`. Use uma versão Graph
   suportada pelo seu app; `v25.0` no exemplo é configurável.
3. Escolha um valor aleatório para `WHATSAPP_VERIFY_TOKEN`.
4. Hospede o serviço Java atrás de um proxy com HTTPS público. Encaminhe
   `/webhooks/whatsapp` à porta do Java, preservando o corpo original e o header
   `X-Hub-Signature-256`. Configure limite de corpo de 1 MiB e timeout de leitura
   no proxy. O listener Java fornece HTTP; o proxy termina o TLS.
5. Cadastre `https://SEU_DOMINIO/webhooks/whatsapp` como callback na Meta e
   informe o mesmo verify token. Assine o campo `messages` e verifique a inscrição
   do app na conta WhatsApp Business utilizada.
6. Envie texto ao número de teste a partir de um destinatário autorizado no painel
   da Meta e confira a resposta e o estado `sent` no banco.

O GET devolve o challenge somente com token correto. O POST valida HMAC-SHA256
do corpo original com o App Secret antes de chamar banco ou IA.

## Conectar a IA existente

O código da SophIA não está neste repositório. Configure `SOPHIA_API_URL` com
um endpoint HTTPS e `SOPHIA_API_TOKEN` com o token aceito por ele.
O adaptador Java está em `Integrations.reply` e envia:

```json
{
  "message": "Olá",
  "conversationId": "whatsapp:PHONE_NUMBER_ID:5582999999999",
  "channel": "whatsapp",
  "phone": "5582999999999",
  "messageId": "wamid.ID"
}
```

O header é `Authorization: Bearer SOPHIA_API_TOKEN`. A resposta esperada é:

```json
{ "reply": "Olá! Como posso ajudar?" }
```

Se o contrato real da SophIA for diferente, adapte `Integrations.reply`.
O endpoint existente não é presumido compatível apenas por ter o nome
`sophia-chat`. A IA deve validar o token, manter histórico por `conversationId`
e usar `messageId` para idempotência. Um telefone recebido não é uma sessão de
usuário autenticado: o acesso a dados privados exige vinculação e autorização.

## Comportamento e limites

- Processa somente texto. Mídia, status e eventos de outro número são ignorados.
  A resposta é limitada a 4096 caracteres Unicode.
- Responde às mensagens recebidas pela Cloud API oficial da Meta. Templates,
  campanhas e contatos proativos não fazem parte desta implementação.
- O claim atômico no banco impede processamento simultâneo do mesmo ID.
  Falhas antes do envio ficam como `failed`; reentregas podem tentar novamente.
- Após iniciar o envio, qualquer falha fica como `uncertain`: a Meta pode ter
  aceitado a resposta mesmo com timeout. Não há reenvio automático desses IDs.
  Confira a entrega na Meta antes de alterar o estado para reprocessamento.
- Interrupções do processo podem deixar mensagens `processing`. Investigue antes
  de alterar o estado. Não existe recuperação automática desses registros.
- O webhook aguarda o processamento, com timeout de 20 segundos por chamada
  externa. Para alto volume ou IA lenta, será necessário adicionar fila durável
  e worker para confirmação imediata. Essa fila não está incluída.

## Testes Java

Na raiz do repositório:

```sh
mkdir -p build/test-classes
javac --release 17 -encoding UTF-8 -d build/test-classes src/backend/java/br/com/kyoto/whatsapp/*.java tests/java/br/com/kyoto/whatsapp/*.java
java -ea -cp build/test-classes br.com.kyoto.whatsapp.WebhookTest
java -ea -cp build/test-classes br.com.kyoto.whatsapp.IntegrationsTest
java -ea -cp build/test-classes br.com.kyoto.whatsapp.HttpTest
```

Os testes simulam banco, IA e envio. Verificam autenticação, fluxo de resposta,
duplicatas concorrentes, lotes, falhas, JSON, limites Unicode, contratos HTTP
e validação de configuração. `HttpTest` abre uma porta temporária somente em
`127.0.0.1`, sem chamar serviços externos. Respostas inesperadas do banco geram
erro para permitir nova tentativa; o envio só é considerado aceito quando a Meta
retorna o ID da mensagem. `sent` indica aceite pela API, não confirmação de leitura
ou entrega ao aparelho. A validação real com
Meta, SophIA e a migração aplicada ainda depende das credenciais do ambiente.

Referências oficiais:
- [Meta: verificação de webhook](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/webhooks/start/)
- [Meta: envio de texto](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/messages/text/)
- [Java: HttpServer](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
- [Java: HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)

As referências Meta descrevem o protocolo; o SDK arquivado não é usado.
