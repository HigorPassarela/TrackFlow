# Design: MVP do Trackflow

## Context

Repositório novo, sem código. Motivação e escopo em `proposal.md`. Comportamentos em `specs/`.

Restrições que moldam o desenho:

- Tem que rodar inteiro na máquina de quem clona o repositório, com um `docker compose up`, sem conta em nuvem.
- O processamento de áudio (ffmpeg) consome bastante CPU e não pode disputar recursos com as requisições da API.
- Os arquivos chegam a 1 GB. Passá-los pela API multiplicaria memória, tempo e custo.
- Por ser portfólio, cada escolha precisa ser explicável no README, e o código precisa servir de exemplo de estilo (`.claude/skills/trackflow-code-style`).

## Goals / Non-Goals

**Goals:**

- Fronteiras de módulo verificadas por teste (Spring Modulith + ArchUnit), não só por convenção.
- Entrega confiável de trabalho entre API e worker: nenhuma versão confirmada fica sem processamento, mesmo que o broker caia no meio.
- Testes de integração com infraestrutura real (Testcontainers) para banco, broker e storage.
- Ambiente de desenvolvimento sem credenciais externas (login de desenvolvimento no perfil `local`).

**Non-Goals:**

- Escalar horizontalmente a API. Uma instância basta; sessões ficam em memória, e trocar para Spring Session JDBC é uma mudança isolada.
- Observabilidade completa com Grafana (o Orbe Bank já mostra isso). Aqui ficam só Actuator, logs estruturados e métricas de processamento.
- Frontend de produto. A página de demonstração existe para gravar o GIF do README e testar o fluxo à mão.

## Decisions

### D1. Três módulos Maven: API, worker e contratos

```
trackflow-service/
├── trackflow-contracts/   records das mensagens (sem Spring)
├── trackflow-api/         Spring Boot MVC + Modulith (dev.higor.trackflow.api)
├── trackflow-worker/      Spring Boot sem web (dev.higor.trackflow.worker)
└── docker/compose.yaml    postgres, rabbitmq, seaweedfs (+ api e worker no profile "app")
```

O worker escala e falha separado da API, e a imagem dele carrega o ffmpeg sem pesar na imagem da API. O módulo de contratos evita que os dois apps dependam um do outro e deixa explícito o formato de cada mensagem.

- *Alternativa: um único app com `@Async`.* Mais simples, mas o ffmpeg disputaria CPU com as requisições, e o projeto deixaria de mostrar comunicação assíncrona entre processos.
- *Alternativa: um repositório por app.* Teria versionamento independente, mas exigiria publicar os contratos como artefato. É exagero para um projeto solo.

### D2. Módulos da API (Spring Modulith)

| Módulo | Responsabilidade | Capabilities | Depende de |
|---|---|---|---|
| `shared` (aberto) | erros (ProblemDetail), config web, `Clock`, geração de tokens | — | — |
| `identity` | usuários, login OAuth2, login dev, `AuthenticatedUser`, `UserDirectory` | identity | shared |
| `project` | projetos, arquivamento, membros, papéis, convites, `ProjectAccess` | projects, collaboration | identity, shared |
| `storage` | port `ObjectStorage` + adapter S3 | — | shared |
| `version` | versões, upload, numeração, reprodução, links de audição, SSE de status | track-versions, collaboration (links), audio-processing (status) | project, storage, identity, shared |
| `processing` | envio de jobs ao worker e recepção dos resultados | audio-processing | version, ableton, shared |
| `comment` | comentários marcados no tempo | timed-comments | version, project, identity, shared |
| `ableton` | snapshots dos `.als` e cálculo de diff | ableton-project-analysis | project, identity, shared |

Capability não é módulo. A capability `collaboration` fica dividida entre `project` (papéis, membros e convites) e `version` (links de audição, que pertencem a uma versão). Separar `project` e `collaboration` em módulos criaria um ciclo: o projeto precisa de `ProjectAccess` para checar papéis, e a colaboração precisaria do projeto para recusar convites em projeto arquivado.

Cada módulo expõe na raiz do pacote só a sua API pública: interfaces de consulta e comando, records e eventos. Todo o resto fica em subpacotes internos (`web`, `application`, `domain`, `adapter`). O teste `ModularityTest` roda `ApplicationModules.verify()` e falha com ciclos ou com acesso a tipos internos.

Exemplos de API pública:

- `project.ProjectAccess`: `requireRole(projectId, userId, minimumRole)` lança `NotFoundException` para quem não é membro e `PermissionDeniedException` para papel insuficiente. `requireRoleOnActiveProject(...)` também recusa projeto arquivado.
- `project.ProjectActivity`: `record(projectId)` atualiza a "última atividade" usada na ordenação da lista. É chamado por `version` e `comment`, na direção permitida das dependências.
- `identity.UserDirectory`: `displayNames(Set<UUID>)` para montar respostas com nome artístico.
- `version.TrackVersions`: consultas (`findSummary`) e transições de processamento (`startProcessing`, `completeProcessing`, `failProcessing`).
- Eventos: `version.VersionConfirmed`, `version.VersionStatusChanged`.

Criar o projeto e registrar o dono como membro acontece na mesma transação, dentro do próprio módulo `project`. Assim o projeto nunca existe sem dono.

### D3. Hexagonal-light: ports só nas fronteiras de infraestrutura

Port e adapter são usados apenas onde a implementação muda entre ambientes ou precisa de um fake em teste:

- `storage.ObjectStorage`: SeaweedFS local e S3 em produção; `InMemoryObjectStorage` nos testes.
- Envio de mensagens: `processing.application.JobPublisher`, com adapter RabbitMQ.
- No worker: `MediaProbe`, `Transcoder` e `LoudnessMeter` (ffmpeg/ffprobe) e `ObjectStorage`.

Módulos de CRUD com regras (`identity`, `project`, `comment`) usam camadas simples: `web → application → domain`, com repositório Spring Data no `domain`.

- *Alternativa: hexagonal completo em todo módulo.* Teria consistência total, mas ports como `ProjectRepositoryPort` com uma única implementação viram cerimônia, e o código de exemplo deixaria de ensinar quando a abstração se paga.
- *Alternativa: camadas simples em tudo.* Seria mais rápido, mas os testes do upload e do processamento dependeriam de S3 e ffmpeg reais.

### D4. Upload direto para o storage com URL pré-assinada

1. O cliente chama `POST /api/projects/{projectId}/versions/uploads` com nome, tipo e tamanho.
2. A API valida papel, formato e tamanho, cria a versão `PENDING_UPLOAD` e assina um `PUT` de 15 minutos com `Content-Type` e `Content-Length` incluídos na assinatura.
3. O cliente envia o arquivo direto ao storage.
4. O cliente chama `POST .../versions/{versionId}/confirm`. A API faz `HEAD` no objeto, compara o tamanho, atribui o número e publica `VersionConfirmed`.

Chaves no bucket `trackflow`: `projects/{projectId}/versions/{versionId}/source.{ext}`, `.../project.als`, `.../preview.mp3` e `.../peaks.json`. As chaves são determinísticas, então reprocessar sobrescreve com segurança.

- *Alternativa: multipart pela API.* Mais simples para o cliente, mas um arquivo de 1 GB prenderia uma thread e memória e exigiria limites no Tomcat. Fica descartada.
- *Alternativa: notificação de evento do storage em vez de `confirm`.* Não depende do cliente, mas a configuração varia entre SeaweedFS e S3 e complica o ambiente local. Pode entrar depois.

Uma tarefa agendada (`@Scheduled`, a cada hora) remove versões `PENDING_UPLOAD` com mais de 24 horas e os objetos delas.

### D5. Numeração com contador por projeto

A tabela `project_version_counters(project_id, last_number)` é atualizada com `UPDATE ... SET last_number = last_number + 1 ... RETURNING last_number` na transação da confirmação. A trava de linha serializa confirmações simultâneas do mesmo projeto, e a restrição `unique(project_id, number)` em `track_versions` é a rede de segurança.

- *Alternativa: `max(number) + 1`.* Gera número repetido sob concorrência.
- *Alternativa: sequence do Postgres.* É global, então deixaria lacunas entre projetos.

### D6. Entrega confiável de jobs: registro de eventos do Modulith + RabbitMQ

`VersionConfirmed` é publicado na transação da confirmação. O registro JDBC de publicações do Modulith grava o evento na mesma transação. O listener `@ApplicationModuleListener` em `processing` roda após o commit e envia `ProcessAudioJob` (e `AnalyzeAbletonProjectJob`, se houver `.als`) ao RabbitMQ. Se o envio falhar, a publicação fica incompleta e é reenviada na próxima inicialização ou pela tarefa de reenvio (a cada 5 minutos, para publicações com mais de 2 minutos). É um outbox sem tabela própria.

- *Alternativa: enviar ao RabbitMQ dentro do serviço.* É dual write: commit sem mensagem, ou mensagem sem commit.
- *Alternativa: `@Externalized` do Modulith.* Publica o próprio evento interno no broker e acopla o formato do evento ao contrato externo. Preferimos traduzir explicitamente para os records de `trackflow-contracts`.
- *Alternativa: tabela de outbox própria.* Daria o mesmo resultado com mais código.

Topologia:

| Exchange (tipo) | Routing key | Fila | Consumidor |
|---|---|---|---|
| `trackflow.jobs` (direct) | `audio.process` | `worker.audio-process` | worker |
| `trackflow.jobs` (direct) | `ableton.analyze` | `worker.ableton-analyze` | worker |
| `trackflow.results` (direct) | `audio.processed` | `api.audio-processed` | API (`processing`) |
| `trackflow.results` (direct) | `ableton.analyzed` | `api.ableton-analyzed` | API (`processing`) |
| `trackflow.dlx` (direct) | nome da fila | `<fila>.dlq` | inspeção manual |

As mensagens são records JSON (Jackson 3) com `messageId`, `jobId`, `versionId` e `occurredAt`.

### D7. Pipeline do worker

Para `ProcessAudioJob`:

1. Baixa o original para um diretório temporário. O arquivo é apagado num `finally`.
2. `ffprobe` (JSON) verifica se há stream de áudio e qual a duração; recusa acima de 20 minutos.
3. `ffmpeg` decodifica o áudio para PCM mono de 22,05 kHz. O stream é lido em Java para calcular os picos (30 pontos por segundo, máximo absoluto normalizado pelo maior pico da faixa, 3 casas decimais) e alimentar o estimador de BPM.
4. `ffmpeg` gera `preview.mp3` (LAME, 192 kbps CBR, 44,1 kHz, estéreo).
5. `ffmpeg -af ebur128=peak=true` mede a loudness integrada e o true peak.
6. Envia `preview.mp3` e `peaks.json` ao storage e publica `AudioProcessed` (sucesso ou falha com motivo).

**Estimador de BPM próprio, em Java puro.** Ele calcula um envelope de onset (fluxo de energia em janelas de 1024 amostras com hop de 512), faz a autocorrelação do envelope entre 60 e 200 BPM, escolhe o pico e o dobra para o intervalo 80–160 quando houver um candidato harmônico com pelo menos 80% da força. Se o pico de autocorrelação for fraco (menos de 0,3 normalizado), não informa BPM (cenário "faixa sem pulso"). Os testes usam um metrônomo sintético gerado no próprio teste.

- *Alternativa: TarsosDSP ou Essentia.* São mais precisos, mas o primeiro não está no Maven Central e o segundo exige um sidecar Python. Um algoritmo próprio e testado é material melhor para o README. A detecção de tom (necessária ao setlist) fica para a change seguinte.

**Classificação de erros.** `InvalidMediaException` (arquivo ilegível, sem áudio, longo demais) gera resultado de falha imediata, sem nova tentativa. Outras exceções passam por retry do listener: 3 tentativas com backoff exponencial de 2 s, 4 s e 8 s. Ao esgotar, um `MessageRecoverer` próprio publica `AudioProcessed` com falha `processing-error` e encaminha a mensagem para a DLQ.

O ffmpeg é chamado por `ProcessBuilder` com argumentos em lista (nunca por string de shell), com timeout de 5 minutos por etapa e stderr capturado para o log.

### D8. Resultados idempotentes na API

`TrackVersion` guarda o `processingJobId` da tentativa em andamento. `completeProcessing(versionId, jobId, analysis)` só aplica o resultado se a versão estiver `PROCESSING` com o mesmo `jobId`; caso contrário registra um log de debug e retorna. Reprocessar gera um novo `jobId`, então resultados atrasados de tentativas antigas são descartados.

Máquina de estados de `TrackVersion.status`:

```
PENDING_UPLOAD --confirm--> UPLOADED --startProcessing--> PROCESSING --complete--> READY
                                ^                              |
                                |                              +--fail--> FAILED
                                +--------------reprocess-------------------+
```

As transições ficam em métodos da entidade, que lançam `BusinessRuleException` quando a transição é inválida.

### D9. Status em tempo real com SSE

`GET /api/projects/{projectId}/events` retorna `SseEmitter` (timeout de 30 minutos e heartbeat a cada 25 s). Um `ProjectEventHub`, interno ao módulo `version`, mantém os emitters por projeto e escuta `VersionStatusChanged` com `@TransactionalEventListener(AFTER_COMMIT)`, para nunca anunciar um estado que sofreu rollback. Com virtual threads habilitadas (`spring.threads.virtual.enabled=true`), conexões longas não esgotam o pool.

- *Alternativa: WebSocket/STOMP.* A comunicação é de mão única (servidor → cliente). SSE é mais simples, atravessa proxies e reconecta sozinho no navegador.
- *Alternativa: polling.* Não atende o limite de 2 segundos sem gerar carga desnecessária.

Limitação: com mais de uma instância da API, cada uma só avisaria os próprios clientes. Isso é aceitável dentro do non-goal de escala horizontal.

### D10. Análise de `.als`

O worker (`AbletonProjectReader`) lê o `.als` (XML compactado com gzip) via StAX em streaming, com `XMLInputFactory` configurado com `SUPPORT_DTD=false` e `IS_SUPPORTING_EXTERNAL_ENTITIES=false`. Um `InputStream` limitador interrompe a leitura acima de 300 MB descompactados. Ele extrai:

- versão do Live: atributo `Creator` do elemento raiz;
- tempo e compasso da track principal: `MasterTrack` no Live 11 e `MainTrack` no Live 12, aceitando os dois nomes;
- tracks (`AudioTrack`, `MidiTrack`, `GroupTrack`, `ReturnTrack`) com o atributo `Id` e o nome efetivo;
- devices: nomes dos elementos filhos da cadeia de devices de cada track;
- plugins: nomes em `VstPluginInfo`, `Vst3PluginInfo` e `AuPluginInfo`.

Os caminhos exatos do XML são levantados com um projeto real do Live 12 na tarefa 11.1 e documentados num comentário de cabeçalho do leitor.

A análise tem ciclo de vida próprio no módulo `ableton` (`PENDING`, `DONE`, `FAILED`), independente do status da versão. Ao receber `VersionConfirmed` com `.als`, o `processing` registra a análise como `PENDING` via `ableton.AbletonAnalyses` e envia o job. O resultado chega como `AbletonAnalyzed` e é guardado em `ableton_analyses` (tracks em JSONB). O diff é uma função pura em `ableton.domain.AbletonDiff`: tracks são pareadas pelo `Id`, então nome diferente com o mesmo `Id` conta como renomeação.

### D11. Identidade e sessão

- Spring Security com `oauth2Login` (GitHub e Google). No primeiro login, um `OAuth2UserService` customizado provisiona a conta por `(provider, provider_subject)`.
- Sessão HTTP com cookie `HttpOnly`, `SameSite=Lax` e `Secure` fora do `local`. CSRF por cookie (`XSRF-TOKEN`) para a página de demonstração e o Postman.
- `AuthenticatedUser`, record da raiz de `identity`, é injetado nos controllers por um `HandlerMethodArgumentResolver`. Os controllers nunca lidam com `OAuth2User`.
- O login de desenvolvimento (`POST /api/dev/login`) existe apenas no bean `DevLoginController`, anotado com `@Profile("local")`. Fora desse profile, o bean não existe e a rota responde 404. Um teste prova isso.
- Não membros recebem 404 em qualquer rota de projeto, para não revelar a existência do recurso. Papel insuficiente recebe 403.

### D12. Tokens de convite e de link de audição

Os tokens têm 32 bytes de `SecureRandom` codificados em Base64 URL-safe. O banco guarda apenas o SHA-256 (`token_hash`). A busca é por hash, e a comparação com `MessageDigest.isEqual`.

### D13. Modelo de dados

| Tabela | Módulo | Colunas principais |
|---|---|---|
| `users` | identity | `id`, `provider`, `provider_subject`, `email`, `display_name`, `avatar_url`, `created_at`; `unique(provider, provider_subject)` |
| `projects` | project | `id`, `title`, `description`, `genre`, `owner_id`, `archived_at`, `last_activity_at`, `created_at`, `updated_at` |
| `project_members` | project | `project_id`, `user_id`, `role`, `joined_at`; PK `(project_id, user_id)` |
| `project_invites` | project | `id`, `project_id`, `token_hash`, `role`, `created_by`, `expires_at`, `accepted_by`, `accepted_at`, `revoked_at` |
| `share_links` | version | `id`, `project_id`, `version_id`, `token_hash`, `created_by`, `expires_at`, `revoked_at` |
| `track_versions` | version | `id`, `project_id`, `number`, `status`, `title`, `notes`, `uploaded_by`, `original_file_name`, `content_type`, `size_bytes`, `source_key`, `als_key`, `preview_key`, `peaks_key`, `duration_ms`, `bpm_estimated`, `bpm_override`, `loudness_lufs`, `true_peak_dbtp`, `failure_reason`, `processing_job_id`, timestamps; `unique(project_id, number)` |
| `project_version_counters` | version | `project_id`, `last_number` |
| `timed_comments` | comment | `id`, `project_id`, `version_id`, `parent_id`, `author_id`, `position_ms`, `end_position_ms`, `body`, `edited_at`, `resolved_at`, `resolved_by`, `deleted_at`, `created_at` |
| `ableton_analyses` | ableton | `version_id`, `project_id`, `status` (`PENDING`, `DONE`, `FAILED`), `failure_reason`, `job_id`, `live_version`, `verified_version`, `tempo`, `time_signature`, `tracks` (jsonb), `analyzed_at` |
| `event_publication` | Modulith | schema oficial do registro JDBC |

- IDs são UUID v7 (`@UuidGenerator(style = VERSION_7)`), ordenáveis por tempo e bons para índice.
- Entre módulos só existem colunas UUID e FKs no banco (`project_id` e `user_id`), nunca relacionamentos JPA (`@ManyToOne`). A integridade fica garantida sem acoplar os modelos.
- Migrations Flyway num único diretório, com o módulo no nome (`V3__project_members.sql`).

### D14. API REST

Prefixo `/api`. Erros seguem RFC 9457 (`ProblemDetail`) com a propriedade extra `code` (`project-not-found`, `insufficient-role`, `version-not-ready`, `invite-expired` etc.). Listas usam paginação `?page=&size=` e retornam `{ items, page, size, totalItems }`. A documentação OpenAPI fica em `/swagger-ui.html` via springdoc.

| Método e rota | Capability |
|---|---|
| `GET/PATCH /api/me`, `POST /logout`, `POST /api/dev/login` | identity |
| `GET/POST /api/projects`, `GET/PATCH /api/projects/{projectId}`, `POST .../archive` e `.../unarchive` | projects |
| `GET .../members`, `PATCH/DELETE .../members/{userId}`, `DELETE .../members/me` | collaboration |
| `GET/POST .../invites`, `DELETE .../invites/{inviteId}`, `POST /api/invites/{token}/accept` | collaboration |
| `POST .../versions/{versionId}/share-links`, `DELETE .../share-links/{linkId}`, `GET /api/listen/{token}` | collaboration |
| `POST .../versions/uploads`, `POST .../versions/{versionId}/confirm`, `POST .../reprocess` | track-versions |
| `GET .../versions`, `GET/PATCH .../versions/{versionId}`, `GET .../playback`, `GET .../download` | track-versions |
| `GET /api/projects/{projectId}/events` (SSE) | audio-processing |
| `GET/POST .../versions/{versionId}/comments`, `POST .../comments/{commentId}/replies` | timed-comments |
| `PATCH/DELETE .../comments/{commentId}`, `POST .../resolve` e `.../reopen` | timed-comments |
| `GET .../versions/{versionId}/ableton`, `GET .../ableton/diff?against={otherVersionId}` | ableton-project-analysis |

### D15. Estratégia de testes

- **Unitários** (JUnit 5, AssertJ, Mockito): entidades e máquinas de estado, `AbletonDiff`, estimador de BPM, cálculo de picos, serviços com fakes.
- **Slice**: `@WebMvcTest` com `MockMvcTester` por controller (validação, status HTTP, ProblemDetail, segurança); `@DataJpaTest` com Postgres do Testcontainers para queries não triviais.
- **Integração**: `@SpringBootTest` com Postgres, RabbitMQ e SeaweedFS via Testcontainers e `@ServiceConnection` (storage com `DynamicPropertySource`). API e worker têm ITs separados, cada um simulando o outro lado pelas mensagens de `trackflow-contracts`.
- **Ponta a ponta**: um script de smoke (`scripts/smoke.sh`) sobe o `docker compose`, faz login de desenvolvimento, envia um WAV de exemplo, espera `READY` e cria um comentário.
- **Arquitetura**: `ApplicationModules.verify()` e regras ArchUnit (sem injeção por campo, controllers só em `..web..`, entidades nunca retornadas por controllers, `java.util.Date` proibido).
- **Testes que exigem ffmpeg** recebem `@Tag("ffmpeg")`. O CI instala o ffmpeg e roda tudo.

### D16. Build, qualidade e CI

- Maven Wrapper; versões fixadas abaixo, conferidas no Maven Central e nos sites oficiais em 21/10/2026. Dependências sem versão na tabela ficam com a que o BOM do Spring Boot gerencia. Não use versões milestone (`-M`, `-RC`).

| Componente | Versão | Como entra |
|---|---|---|
| Java | 25 (LTS) | `java.version` e imagem `eclipse-temurin:25-jre` |
| Spring Boot | 4.1.1 | parent `spring-boot-starter-parent` |
| Spring Modulith | 2.1.1 | BOM `spring-modulith-bom` |
| Testcontainers | 2.0.5 | BOM `testcontainers-bom` (módulos `testcontainers-postgresql`, `testcontainers-rabbitmq`) |
| AWS SDK v2 | 2.57.2 | BOM `software.amazon.awssdk:bom` (`s3`) |
| springdoc-openapi | 3.1.1 | `springdoc-openapi-starter-webmvc-ui` (a linha 3.x é a do Boot 4) |
| Lombok | 1.18.48 | `lombok` (provided) + `annotationProcessorPaths` |
| ArchUnit | 1.5.1 | `archunit-junit5` (test) |
| PostgreSQL | 18 | imagem `postgres:18-alpine` (a 19 ainda está em RC) |
| RabbitMQ | 4.4 | imagem `rabbitmq:4.4-management` (a 4.2 já saiu do suporte da comunidade) |
| SeaweedFS | última estável | imagem `chrislusf/seaweedfs` com tag fixa, escolhida na tarefa 1.4 |

Para atualizar: troque a versão aqui e no POM no mesmo commit.
- Spotless com palantir-java-format, verificado no `verify`.
- `trackflow-api` e `trackflow-worker` geram imagem por `spring-boot:build-image`. O worker usa Dockerfile próprio sobre `eclipse-temurin:25-jre` com ffmpeg instalado.
- GitHub Actions: `setup-java` 25 com cache do Maven, `apt-get install ffmpeg` e `./mvnw -B verify`. Badge no README.

## Risks / Trade-offs

- [O formato do `.als` não é documentado e muda entre versões do Live] → leitor tolerante (ignora o que não conhece, aceita `MasterTrack` e `MainTrack`), fixtures reais do Live 11 e 12 e marcação de "versão não verificada".
- [O estimador de BPM próprio erra em faixas com swing ou quebradas] → valor sempre rotulado como estimativa, correção manual (`bpm_override`) e omissão quando a confiança é baixa.
- [O cliente pode não chamar `confirm` depois do upload] → limpeza de pendentes em 24 h. O custo é armazenar objetos órfãos por até um dia.
- [URL pré-assinada vazada permite enviar arquivo] → validade de 15 minutos, tamanho e tipo assinados, e o objeto só vira versão após `confirm` autenticado.
- [Arquivos maliciosos explorando o ffmpeg] → o worker roda isolado, sem credenciais de banco, com usuário sem privilégios na imagem, timeout por etapa e ffmpeg mantido atualizado pela imagem base.
- [SSE em memória não funciona com várias instâncias] → aceito pelo non-goal; a evolução seria publicar `VersionStatusChanged` num exchange fanout do RabbitMQ.
- [Java 25 é mais novo que a stack do Orbe Bank (21)] → nenhuma feature exclusiva do 25 é obrigatória; voltar ao 21 é trocar `java.version` e a imagem base.

## Migration Plan

Projeto novo, sem dados a migrar. Para colocar no ar como demo:

1. Banco gerenciado (Postgres 18) com Flyway na inicialização.
2. CloudAMQP (plano gratuito) ou RabbitMQ em container.
3. Bucket S3 ou compatível (Cloudflare R2 dispensa custo de saída), com CORS liberando `PUT` e `GET` para o domínio da demo.
4. Apps OAuth no GitHub e no Google com a URL de callback da demo.

Rollback: redeploy da imagem anterior. As migrations do MVP são aditivas.

## Open Questions

- Onde hospedar a demo pública (Fly.io, Railway ou VPS). Não afeta specs nem tarefas, só o passo final de deploy.
- Se vale trocar o preview MP3 por Opus quando o Safari tiver suporte consistente. Hoje MP3 é o mais compatível.
