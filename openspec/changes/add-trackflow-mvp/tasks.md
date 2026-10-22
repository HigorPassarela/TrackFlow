# Tasks

## 1. Fundação do repositório

- [x] 1.1 Criar o POM pai com os módulos `trackflow-contracts`, `trackflow-api` e `trackflow-worker`, usando exatamente as versões da tabela do D16 do `design.md`, e o Maven Wrapper; verificar que `./mvnw -B verify` passa com os módulos vazios
- [x] 1.2 Configurar o Spotless com palantir-java-format na fase `verify` e verificar que um arquivo mal formatado faz `./mvnw verify` falhar e que `./mvnw spotless:apply` corrige
- [x] 1.3 Criar o `lombok.config` na raiz proibindo `@Data`, `@Value` e `@Setter` (`flagUsage = error`) e verificar que uma classe de teste com `@Data` não compila
- [x] 1.4 Criar o `docker/compose.yaml` com PostgreSQL 18, RabbitMQ 4.4 (management) e SeaweedFS (tags fixas da tabela D16) (gateway S3, bucket `trackflow` criado na subida, CORS para `PUT`/`GET`), todos com healthcheck, e verificar que `docker compose up -d` deixa os três serviços `healthy`
- [x] 1.5 Criar o `.env.example` com todas as variáveis (OAuth, storage, banco) e o `.gitignore`, e verificar que `git status` não lista `.env` nem `target/`
- [ ] 1.6 Criar o workflow do GitHub Actions (setup-java 25 com cache do Maven, instalação do ffmpeg, `./mvnw -B verify`) e verificar que o workflow fica verde num push

## 2. Base da API e verificação de arquitetura

- [x] 2.1 Criar `TrackflowApiApplication` em `dev.higor.trackflow.api` com Actuator, virtual threads e logs estruturados no profile padrão, e verificar com teste que `/actuator/health` responde `UP`
- [x] 2.2 Criar `ModularityTest` com `ApplicationModules.verify()` e as regras ArchUnit da skill de estilo (sem injeção por campo, controllers só em `..web..`, entidades fora das respostas HTTP, sem `java.util.Date`); verificar que o teste passa e que falha ao introduzir de propósito um acesso a subpacote interno de outro módulo
- [x] 2.3 Gerar a documentação de módulos do Modulith (`Documenter`) em `docs/modules/` dentro do `ModularityTest` e verificar que os arquivos são gerados
- [x] 2.4 Implementar `shared.error` (`DomainException`, `NotFoundException`, `PermissionDeniedException`, `BusinessRuleException`) e o `GlobalExceptionHandler` com ProblemDetail e propriedade `code`, e verificar com `@WebMvcTest` o formato de 400, 403, 404 e 409
- [x] 2.5 Expor o bean `Clock` e o `TokenGenerator` (32 bytes de `SecureRandom`, Base64 URL-safe, hash SHA-256) no `shared`, e verificar com testes unitários o tamanho do token e a estabilidade do hash
- [x] 2.6 Configurar o Flyway com a migration `V1__modulith_event_publication.sql` e criar um IT base com Postgres via Testcontainers e `@ServiceConnection`; verificar que o contexto sobe com as migrations aplicadas
- [x] 2.7 Adicionar o springdoc-openapi e verificar com teste que `/v3/api-docs` responde 200

## 3. Identidade

- [x] 3.1 Criar a migration `users`, a entidade `User` e o repositório, e verificar com `@DataJpaTest` a unicidade de `(provider, provider_subject)`
- [x] 3.2 Configurar o Spring Security: `oauth2Login` com GitHub e Google, 401 em JSON para `/api/**`, CSRF por cookie e rotas públicas (health, demo, `/api/listen/**`); verificar com `@WebMvcTest` o 401 sem sessão e o 403 em `POST` sem token CSRF
- [x] 3.3 Implementar o provisionamento de conta no `OAuth2UserService` e verificar com testes unitários os três cenários: primeiro login, login recorrente e mesmo e-mail em outro provedor
- [x] 3.4 Criar o record `AuthenticatedUser` e o `HandlerMethodArgumentResolver`, e verificar com teste de controller que o parâmetro chega preenchido
- [x] 3.5 Implementar `GET` e `PATCH /api/me` com validação de 2 a 60 caracteres e verificar com `@WebMvcTest` o sucesso e o erro de validação com o campo indicado
- [x] 3.6 Configurar o logout com invalidação de sessão e verificar com teste que a sessão antiga recebe 401
- [x] 3.7 Criar o `DevLoginController` com `@Profile("local")` e verificar com dois testes: funciona no profile `local` e responde 404 no profile padrão
- [x] 3.8 Criar `identity.UserDirectory.displayNames(...)` e verificar com teste de integração a busca em lote
- [ ] 3.9 Documentar no README como criar o OAuth App do GitHub e usar o login de desenvolvimento, e verificar seguindo o passo a passo com o compose local

## 4. Projetos, membros e convites

- [x] 4.1 Criar as migrations `projects` e `project_members`, as entidades `Project` e `ProjectMember` e o enum `ProjectRole`, e verificar com teste unitário as transições de arquivar e desarquivar
- [x] 4.2 Implementar `ProjectAccess` (`requireRole`, `requireRoleOnActiveProject`) e verificar com testes a matriz completa: não membro gera 404, papel insuficiente gera 403 e projeto arquivado gera 409
- [x] 4.3 Implementar a criação de projeto registrando o dono como membro na mesma transação e verificar com IT que uma falha ao gravar o membro desfaz o projeto
- [x] 4.4 Implementar a listagem paginada dos meus projetos ordenada por `last_activity_at`, com papel e filtro de arquivados, mais `ProjectActivity.record`; verificar com `@DataJpaTest` a ordem e o filtro
- [x] 4.5 Implementar consulta, edição, arquivamento e desarquivamento com as regras de papel, e verificar com `@WebMvcTest` os cenários da spec `projects`
- [x] 4.6 Implementar os endpoints de membros (listar, alterar papel, remover, sair) com as proteções do dono, e verificar com testes os cenários de gerenciar membros e sair do projeto
- [x] 4.7 Criar a migration `project_invites` e implementar criar, listar pendentes e revogar convite, e verificar que o token só aparece na resposta de criação e que o banco guarda apenas o hash
- [x] 4.8 Implementar o aceite de convite e verificar com testes cada cenário: válido, expirado, já usado, revogado e usuário já membro (este sem consumir o convite)

## 5. Storage

- [x] 5.1 Definir o port `storage.ObjectStorage` (assinar `PUT` com tamanho e tipo, assinar `GET` com nome de download, `HEAD`, `delete`) e os records de retorno, e verificar que compila sem dependência do AWS SDK no pacote raiz
- [x] 5.2 Implementar o `S3ObjectStorage` com AWS SDK v2 e `S3Presigner` (path-style para o SeaweedFS) e verificar com IT contra SeaweedFS no Testcontainers que um `PUT` pelo endereço assinado e o `HEAD` seguinte retornam o tamanho certo
- [x] 5.3 Criar o `InMemoryObjectStorage` em `src/test` e verificar que os testes de serviço de `version` o usam sem subir container

## 6. Versões e upload

- [x] 6.1 Criar as migrations `track_versions` e `project_version_counters` e a entidade `TrackVersion` com a máquina de estados, e verificar com testes unitários todas as transições válidas e que as inválidas lançam `BusinessRuleException`
- [x] 6.2 Implementar `POST .../versions/uploads` com validação de papel, projeto ativo, formatos, limite de 1 GB e anexo `.als` de até 50 MB, e verificar com `@WebMvcTest` cada cenário de aceite e recusa
- [x] 6.3 Implementar `POST .../confirm` com `HEAD` e comparação de tamanho, numeração pelo contador, idempotência, `ProjectActivity.record` e publicação de `VersionConfirmed`, e verificar com IT os cenários válido, ausente, divergente e repetido
- [x] 6.4 Escrever um IT de concorrência com duas confirmações simultâneas no mesmo projeto e verificar que os números saem 4 e 5, sem erro
- [x] 6.5 Implementar a limpeza agendada de `PENDING_UPLOAD` com mais de 24 horas, removendo os objetos, e verificar com teste usando `Clock` fixo
- [x] 6.6 Implementar o histórico paginado, o detalhe da versão e o `PATCH` de título, notas e BPM informado (40 a 250), e verificar com `@WebMvcTest` as permissões e as validações
- [x] 6.7 Implementar `GET .../playback` (só `READY`, URL de 1 hora) e `GET .../download` (só dono e editor, com nome original), e verificar com testes os cenários de versão não pronta e de ouvinte pedindo original
- [x] 6.8 Implementar `POST .../reprocess` (só `FAILED`, novo `jobId`) e verificar com testes o aceite e a recusa para versão pronta

## 7. Contratos e envio de jobs

- [x] 7.1 Criar em `trackflow-contracts` os records `ProcessAudioJob`, `AnalyzeAbletonProjectJob`, `AudioProcessed` e `AbletonAnalyzed`, e verificar com teste de serialização Jackson 3 o round trip de cada um
- [x] 7.2 Declarar os nomes da topologia do RabbitMQ (tabela D6) como constantes em `trackflow-contracts` e, em cada app, os beans das filas que ele consome (com DLX e DLQ), e verificar com IT contra RabbitMQ no Testcontainers que as filas existem com `x-dead-letter-exchange`
- [x] 7.3 Implementar o `JobPublisher` (port) com adapter RabbitMQ e o listener `@ApplicationModuleListener` de `VersionConfirmed` que envia os jobs e registra a análise do Ableton como `PENDING`; verificar com IT que a confirmação gera mensagem na fila `worker.audio-process`
- [x] 7.4 Configurar o registro JDBC de publicações com reenvio de incompletas e verificar com IT que, com o RabbitMQ parado no envio, a mensagem chega depois que ele volta e o reenvio roda

## 8. Worker: processamento de áudio

- [x] 8.1 Criar `TrackflowWorkerApplication` sem servidor web, com Actuator e o mesmo `ObjectStorage` (adapter S3), e verificar com teste que o contexto sobe
- [x] 8.2 Criar o Dockerfile do worker sobre `eclipse-temurin:25-jre` com ffmpeg e usuário sem privilégios, e verificar que `docker run ... ffmpeg -version` funciona na imagem
- [x] 8.3 Implementar `MediaProbe` (ffprobe JSON) com `ProcessBuilder` em lista de argumentos e timeout, e verificar com testes `@Tag("ffmpeg")` os casos WAV válido, arquivo corrompido, vídeo sem áudio e duração acima de 20 minutos
- [x] 8.4 Implementar a decodificação para PCM e o `PeaksCalculator` (30 pontos por segundo, normalizado), e verificar com teste unitário de sinal sintético os cenários de silêncio igual a 0 e pico igual a 1
- [x] 8.5 Implementar o `BpmEstimator` (envelope de onset e autocorrelação) e verificar com metrônomos sintéticos de 124 e 128 BPM (tolerância ±1) e um pad contínuo (sem BPM)
- [x] 8.6 Implementar o `Transcoder` para o preview MP3 de 192 kbps e o `LoudnessMeter` (ebur128 com true peak), e verificar com testes `@Tag("ffmpeg")` a duração do preview (±50 ms) e a loudness de um seno de referência (±0,5 LU)
- [x] 8.7 Implementar o listener de `ProcessAudioJob` com extração de áudio de vídeo, envio do preview e dos picos e publicação de `AudioProcessed`, e verificar com IT (RabbitMQ e SeaweedFS no Testcontainers) o fluxo de um WAV e de um MP4
- [x] 8.8 Configurar retry (3 tentativas, backoff de 2, 4 e 8 s), não repetir em `InvalidMediaException` e o `MessageRecoverer` que publica a falha e encaminha para a DLQ, e verificar com testes os cenários de falha temporária recuperada, falha persistente e arquivo inválido sem retry

## 9. Resultados, status e tempo real

- [x] 9.1 Implementar o listener de `AudioProcessed` na API chamando `TrackVersions.completeProcessing` ou `failProcessing` com checagem de `jobId`, e verificar com testes o resultado duplicado e o resultado de tentativa antiga sendo ignorados
- [x] 9.2 Publicar `VersionStatusChanged` em cada transição e implementar o `ProjectEventHub` com `SseEmitter`, heartbeat e `@TransactionalEventListener(AFTER_COMMIT)`, e verificar com IT que um cliente conectado recebe o evento de `READY` em até 2 segundos
- [x] 9.3 Proteger `GET /api/projects/{projectId}/events` com `ProjectAccess` e verificar com teste que não membro recebe 404
- [x] 9.4 Adicionar métricas Micrometer (tempo de processamento, sucesso e falha por motivo) e verificar com teste que aparecem em `/actuator/metrics`

## 10. Links de audição

- [x] 10.1 Criar a migration `share_links` e implementar criar (dono e editor, versão `READY`, 1 a 30 dias, padrão 7) e revogar (dono ou criador), e verificar com testes os cenários de permissão e de versão não pronta
- [x] 10.2 Implementar o endpoint público `GET /api/listen/{token}` retornando só os dados permitidos pela spec, e verificar com testes o link válido, expirado e revogado (os dois últimos com 404) e a ausência de comentários na resposta

## 11. Projeto do Ableton

- [ ] 11.1 Levantar a estrutura XML com um `.als` real do Live 12 e outro do Live 11, criar fixtures mínimas em `src/test/resources` e documentar os caminhos usados no cabeçalho do `AbletonProjectReader`; verificar que as fixtures abrem no leitor de teste
- [x] 11.2 Implementar o `AbletonProjectReader` com StAX (DTD e entidades externas desativadas) e limite de 300 MB descompactados, e verificar com testes o resumo esperado, o arquivo com entidade externa e a bomba de descompressão
- [x] 11.3 Implementar o listener de `AnalyzeAbletonProjectJob` no worker publicando `AbletonAnalyzed` (sucesso ou falha) e verificar com IT o fluxo completo de um `.als` de fixture
- [x] 11.4 Criar a migration `ableton_analyses` e a API pública `AbletonAnalyses` no módulo `ableton` (registrar pendente, concluir, falhar, com checagem de `jobId`), e verificar com teste que uma falha da análise não altera o status da versão
- [x] 11.5 Implementar o `AbletonDiff` como função pura (tempo, tracks por `Id` com renomeação, devices por track, plugins) e verificar com testes unitários os cenários da spec
- [x] 11.6 Implementar `GET .../ableton` e `GET .../ableton/diff?against=` com `ProjectAccess` e verificar com `@WebMvcTest` o ouvinte consultando, a versão sem análise e as versões de projetos diferentes (404)

## 12. Comentários marcados no tempo

- [x] 12.1 Criar a migration `timed_comments` e a entidade `TimedComment` (posição, trecho, fio, resolução, exclusão lógica), e verificar com testes unitários as regras de trecho e de exclusão com e sem respostas
- [x] 12.2 Implementar criar comentário e responder (resposta a resposta vai para o fio original), com versão `READY`, limites de posição e `ProjectActivity.record`, e verificar com `@WebMvcTest` os cenários da spec
- [x] 12.3 Implementar a listagem por posição com respostas em ordem cronológica, nome artístico via `UserDirectory` e filtro de não resolvidos, e verificar com `@DataJpaTest` a ordem e o filtro
- [x] 12.4 Implementar editar (só o autor), excluir (autor ou dono) e resolver ou reabrir (autor, dono e editores), e verificar com testes cada regra de permissão

## 13. Demonstração e verificação final

- [ ] 13.1 Criar a página de demonstração estática servida pela API (login, lista de projetos, upload direto ao storage, player com forma de onda e marcadores de comentário) e verificar à mão o fluxo completo no navegador
- [x] 13.2 Criar o `scripts/smoke.sh` (compose, login de desenvolvimento, upload de WAV de exemplo, espera de `READY`, comentário) e verificar que ele termina com código 0 num checkout limpo
- [ ] 13.3 Completar o README com o diagrama de arquitetura, a tabela de módulos, as decisões principais (link para o `design.md`), o GIF da demo e o badge do CI, e verificar que todos os links e comandos do README funcionam
- [x] 13.4 Rodar `./mvnw -B verify` com o ffmpeg instalado e `openspec validate add-trackflow-mvp --strict`, e verificar que os dois passam sem avisos

## Workflow follow-up

- Revisar o código contra a skill `trackflow-code-style` antes do PR final.
- Arquivar a change com `/opsx:archive` depois do merge.
- Abrir a change `add-dj-setlists`.
