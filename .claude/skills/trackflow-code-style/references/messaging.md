# Eventos e mensagens

## Eventos entre módulos (dentro da API)

- Record na raiz do módulo que publica, no passado, com só ids e dados que o consumidor precisa: `record VersionConfirmed(UUID versionId, UUID projectId, int number, String sourceKey, @Nullable String abletonKey) {}`.
- Publicado pelo serviço com `ApplicationEventPublisher.publishEvent(...)` dentro da transação. O registro de publicações do Modulith garante a entrega mesmo se a aplicação cair depois do commit.
- Consumido com `@ApplicationModuleListener` (assíncrono, depois do commit, em transação nova). O listener é package-private e mora em `application` do módulo consumidor.
- Para efeito imediato na mesma requisição (SSE), use `@TransactionalEventListener(phase = AFTER_COMMIT)`. Nunca `@EventListener` simples para algo que não pode acontecer em rollback.
- Listener não lança exceção para "já processado": verifica e retorna.

## Mensagens entre API e worker (RabbitMQ)

- O contrato é um record em `trackflow-contracts` com `messageId` (UUID), `jobId`, ids de negócio e `occurredAt`. Nomes de exchange e routing key ficam em `Topology` (constantes).
- Quem publica é um adapter atrás de um port (`JobPublisher`). O serviço de aplicação não conhece `RabbitTemplate`.
- Conversão JSON com um bean `JacksonJsonMessageConverter` (Jackson 3). Cada fila tem um único tipo de mensagem e o listener declara esse tipo no parâmetro. Para hierarquias sealed (`AudioProcessed`), o subtipo vem de um nome lógico (`@JsonTypeInfo(use = NAME)`), nunca do nome da classe Java.
- Toda fila tem DLX e DLQ. Retry acontece no consumidor (3 tentativas, backoff exponencial). Erro de dado (arquivo inválido) não é retentado.

## Idempotência

Todo consumidor assume que a mensagem pode chegar duas vezes ou atrasada:

1. A mensagem carrega o `jobId` da tentativa.
2. O agregado guarda o `jobId` em andamento.
3. O método de domínio só aplica o resultado se o estado e o `jobId` baterem; senão registra em debug e retorna.

Saídas do worker usam chaves determinísticas no storage (`.../preview.mp3`), então reprocessar sobrescreve em vez de duplicar.

## Processos externos (worker)

- `ProcessBuilder` com argumentos em `List<String>`, nunca uma string passada a `sh -c`.
- Timeout explícito, `destroyForcibly()` ao estourar, stderr capturado (truncado) para o log e para a mensagem de erro.
- Arquivos temporários em diretório próprio da tarefa, apagado em `finally`.
- Saída de ferramenta (`ffprobe -of json`) é parseada para records. Nunca extraia valores com regex em texto livre se existe saída estruturada.
