# Persistência

## Entidades

- Ficam em `<módulo>.domain`, com `@Table(name = "...")` explícito no plural snake_case.
- ID: `@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) private UUID id;`. O id existe a partir do `save`, então eventos com o id são publicados depois de salvar.
- Concorrência otimista com `@Version private long rowVersion;` (coluna `row_version`). O nome evita confusão com "versão da faixa".
- Enums: `@Enumerated(EnumType.STRING)`. Nunca `ORDINAL`.
- Timestamps: `Instant`, recebidos do serviço (que lê o `Clock`). Sem `@CreationTimestamp`, porque o teste precisa controlar o tempo.
- Referência a outro agregado, mesmo do mesmo módulo, é pelo id (`private UUID parentId`). `@OneToMany` e `@ManyToOne` só dentro de um agregado que é carregado e salvo junto, o que é raro aqui.
- Não sobrescreva `equals`/`hashCode`. Compare entidades pelo `getId()`.
- JSON (`tracks` do Ableton): `@JdbcTypeCode(SqlTypes.JSON)` num campo do tipo record ou `List<record>`.

## Invariantes

A entidade garante a própria consistência:

- O construtor privado do `@Builder` valida e lança `IllegalArgumentException` para dados impossíveis, que indicam bug, e `BusinessRuleException` para regras que o usuário pode violar.
- Transições de estado são métodos (`markReady(...)`, `resolve(...)`) que checam o estado atual. Se a transição é inválida, lançam `BusinessRuleException` com código específico.
- Getters existem (`@Getter`), setters não.

## Repositórios

- Interface Spring Data em `<módulo>.domain`, estendendo `ListCrudRepository<T, UUID>` (e `ListPagingAndSortingRepository` se paginar).
- Toda busca de recurso que pertence a um projeto recebe o `projectId`: `Optional<TimedComment> findByIdAndProjectId(UUID id, UUID projectId)`.
- Queries derivadas até três critérios simples. Acima disso, ou com `or`, `@Query` com JPQL e parâmetros nomeados (o build compila com `-parameters`, então `:projectId` casa com o nome do parâmetro).
- Leituras para tela que juntam dados viram projeção em record (`select new ...View(...)`) em vez de carregar entidades inteiras.
- Atualizações atômicas (contador de versão) usam `@Modifying @Query` nativa com `RETURNING`, documentada no próprio método.

## Transações

- `@Transactional` nos métodos públicos de serviço que escrevem; `@Transactional(readOnly = true)` nos que só leem. Nunca em controller ou repositório.
- Uma transação por caso de uso. Não chame serviços que abrem transação nova dentro de um loop.
- Chamadas externas (S3, RabbitMQ, ffmpeg) ficam **fora** da transação ou depois do commit (`@ApplicationModuleListener`, `@TransactionalEventListener`). A única exceção é a assinatura de URL, que é cálculo local.

## Migrations (Flyway)

- `trackflow-api/src/main/resources/db/migration/V<n>__<modulo>_<descricao>.sql`, como `V9__comment_timed_comments.sql`.
- Nunca edite uma migration mergeada; crie a próxima.
- Toda tabela tem PK, `created_at timestamptz not null` e FKs com `on delete` explícito.
- Índices para toda coluna usada em `where` ou `order by` de listagem, com o nome `ix_<tabela>_<colunas>`. Restrições: `uq_`, `fk_`, `ck_`.
- `check` constraints para regras simples e estáveis (posição >= 0, papel em lista fechada). Regras que mudam ficam no código.
