---
name: trackflow-code-style
description: Como o código Java/Spring do Trackflow deve ser escrito. Use sempre antes de criar, alterar ou revisar código em trackflow-api, trackflow-worker ou trackflow-contracts, inclusive testes e migrations.
---

# Estilo de código do Trackflow

Este é o jeito de escrever código neste repositório. Os exemplos em `examples/` são a referência concreta: quando estiver em dúvida, abra o exemplo do mesmo tipo de arquivo e siga o padrão. Se algo aqui contradisser o `design.md` da change em andamento, o design vence. Avise a divergência.

## Antes de escrever

1. Descubra a qual módulo do Modulith o código pertence (tabela D2 do `design.md`).
2. Abra o exemplo correspondente (mapa em `examples/README.md`).
3. Leia a referência do assunto, se for a primeira vez nesta sessão:
   - pacotes, módulos e ports → `references/architecture.md`
   - Java, Lombok, records, nulos, nomes → `references/java-style.md`
   - controllers, DTOs, validação, erros HTTP → `references/web-api.md`
   - entidades, repositórios, transações, migrations → `references/persistence.md`
   - eventos, RabbitMQ, idempotência, worker → `references/messaging.md`
   - testes → `references/testing.md`

## As regras que mais importam

**Arquitetura**
- A raiz do pacote do módulo é a API pública dele. Subpacotes (`web`, `application`, `domain`, `adapter`) são internos e nunca são importados por outro módulo.
- Port e adapter só em fronteira de infraestrutura (storage, broker, ffmpeg). CRUD usa `web → application → domain`, sem interface "por via das dúvidas".
- Toda query de recurso de projeto é filtrada por `projectId` além do id do recurso, e toda operação começa checando o papel com `ProjectAccess`.

**Java**
- Records para DTOs, comandos, eventos, mensagens e views. Lombok só em entidades JPA: `@Getter`, `@NoArgsConstructor(access = PROTECTED)` e `@Builder` num construtor privado que valida invariantes. Sem `@Data`, `@Setter` ou `@Value`; o `lombok.config` barra.
- Injeção por construtor único, campos `private final`, sem `@Autowired`.
- Métodos públicos nunca retornam `null`. `Optional` só como retorno de busca, nunca em campo ou parâmetro. Use `@Nullable` (JSpecify) onde `null` é permitido.
- Tempo vem de `Clock` injetado e é sempre `Instant` ou `Duration`. Nada de `LocalDateTime.now()` ou `new Date()`.
- Estado muda por método com nome de negócio (`resolve`, `confirm`, `archive`), nunca por setter.

**Erros e API**
- Regras de negócio lançam as exceções de `shared.error` (`NotFoundException`, `PermissionDeniedException`, `BusinessRuleException`) com um `code` em kebab-case. O `GlobalExceptionHandler` transforma em ProblemDetail.
- Não membro recebe 404 (`NotFoundException`), nunca 403.
- Controllers são package-private, recebem `*Request` com `@Valid`, chamam um serviço e devolvem `*Response`. Nunca expõem entidades.

**Dados**
- IDs UUID v7, enums como `STRING`, timestamps `Instant` (coluna `timestamptz`).
- Sem `@ManyToOne` entre módulos: só a coluna UUID e a FK no banco.
- Migration nova a cada mudança de schema. Nunca edite uma migration já mergeada.

**Mensagens**
- Entre módulos: evento de domínio (record no passado, `VersionConfirmed`) e `@ApplicationModuleListener`.
- Entre API e worker: records de `trackflow-contracts`, publicados após o commit. Todo consumidor é idempotente.

**Testes**
- Toda regra de negócio tem teste unitário; todo endpoint tem teste `@WebMvcTest` cobrindo sucesso, validação e permissão; toda query não trivial tem `@DataJpaTest` com Postgres real.
- Nomes de teste em inglês descrevendo o comportamento (`rejectsCommentBeyondVersionDuration`), asserções com AssertJ.

## Checklist antes de dizer que terminou

- [ ] `./mvnw -B verify` passa, incluindo Spotless e `ModularityTest`.
- [ ] Nenhum import de subpacote interno de outro módulo.
- [ ] Cada endpoint novo checa papel e tem teste de 404 para não membro.
- [ ] Nenhuma entidade sai de um controller; nenhum setter foi criado.
- [ ] Migrations novas rodam do zero e não alteram migrations antigas.
- [ ] Logs em inglês, com contexto (`versionId=...`) e sem tokens, URLs assinadas ou e-mails.
- [ ] Os cenários da spec tocados pela tarefa têm teste correspondente.
