# Mapa dos exemplos

Os caminhos espelham o repositório real (`trackflow-api/src/main/java/...`), então o exemplo de um tipo de arquivo está onde esse tipo de arquivo vai morar. O módulo `comment` é a fatia vertical completa: domínio, aplicação, web, migration e os quatro tipos de teste.

| Vou escrever... | Abra |
|---|---|
| Entidade JPA com Lombok, builder validado e transições de estado | `comment/domain/TimedComment.java` |
| Value object (record com construtor compacto) | `comment/domain/TimeRange.java` |
| Repositório com query escopada por projeto e `@Query` | `comment/domain/TimedCommentRepository.java` |
| Serviço de aplicação (permissão → regra → persistência → evento) | `comment/application/TimedCommentService.java` |
| Comando e view da camada de aplicação | `comment/application/AddComment.java`, `CommentView.java` |
| Controller package-private | `comment/web/TimedCommentController.java` |
| Request com Bean Validation e validação entre campos | `comment/web/CreateCommentRequest.java` |
| Response separado da view | `comment/web/CommentResponse.java` |
| Evento de domínio publicado por um módulo | `comment/CommentAdded.java`, `version/VersionConfirmed.java` |
| API pública de um módulo (interface na raiz) | `project/ProjectAccess.java`, `version/TrackVersions.java`, `identity/UserDirectory.java` |
| `package-info.java` de módulo | `comment/package-info.java`, `shared/package-info.java` |
| Exceções de domínio e tratamento HTTP | `shared/error/*` |
| Port de infraestrutura + adapter | `storage/ObjectStorage.java` + `storage/adapter/S3ObjectStorage.java` |
| `@ConfigurationProperties` em record | `storage/adapter/StorageProperties.java` |
| Listener de evento entre módulos que publica no broker | `processing/application/VersionJobDispatcher.java` |
| Publicação no RabbitMQ com confirmação | `processing/adapter/RabbitJobPublisher.java` |
| Consumidor de mensagem idempotente | `processing/adapter/AudioProcessedListener.java` |
| Contrato de mensagem (record e sealed com tipo lógico) | `trackflow-contracts/.../ProcessAudioJob.java`, `AudioProcessed.java`, `Topology.java` |
| Chamada a processo externo (ffmpeg) | `trackflow-worker/.../process/ProcessRunner.java` |
| Migration Flyway | `resources/db/migration/V9__comment_timed_comments.sql` |
| `application.yml` | `resources/application.yml` |
| Teste unitário de domínio | `test/.../comment/domain/TimedCommentTest.java`, `TimeRangeTest.java` |
| Teste de serviço com Mockito | `test/.../comment/application/TimedCommentServiceTest.java` |
| Teste de controller com `MockMvcTester` | `test/.../comment/web/TimedCommentControllerTest.java` |
| Teste de repositório com Postgres real | `test/.../comment/domain/TimedCommentRepositoryIT.java` + `support/PostgresTestConfiguration.java` |
| Fake de port para testes | `test/.../storage/InMemoryObjectStorage.java` |
| Regras de arquitetura | `test/.../architecture/ModularityTest.java` |
| Configuração do Lombok | `lombok.config` |

## O que estes exemplos não mostram (e de onde vem)

- O resolver que injeta `AuthenticatedUser` nos controllers e a configuração de segurança são do módulo `identity` (tarefas 3.2 e 3.4). O teste de controller assume que eles existem.
- `ProjectAccess`, `TrackVersions` e `UserDirectory` aparecem só como interfaces: as implementações seguem o mesmo padrão do `TimedCommentService`.
- Editar e excluir comentários (tarefa 12.4) seguem o padrão de `resolve`. A migration de exemplo já tem as colunas `edited_at` e `deleted_at`, mas a entidade de exemplo ainda não as mapeia: isso entra na tarefa 12.1.

Os exemplos são referência de estilo, não código para copiar inteiro: o comportamento correto é o das specs da change.
