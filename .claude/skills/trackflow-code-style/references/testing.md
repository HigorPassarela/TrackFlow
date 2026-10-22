# Testes

## O que testar e com qual ferramenta

| Alvo | Tipo | Ferramentas | Sufixo |
|---|---|---|---|
| Entidade, value object, cálculo puro | unitário | JUnit 5 + AssertJ | `Test` |
| Serviço de aplicação | unitário | Mockito para ports e APIs de outros módulos, fakes (`InMemoryObjectStorage`) | `Test` |
| Controller | slice | `@WebMvcTest` + `MockMvcTester` + `@MockitoBean` do serviço | `Test` |
| Query não trivial | slice | `@DataJpaTest` + Postgres (Testcontainers) | `IT` |
| Fluxo com broker, storage ou ffmpeg | integração | `@SpringBootTest` + Testcontainers | `IT` |
| Fronteiras de módulo | arquitetura | Spring Modulith + ArchUnit | `ModularityTest` |

Testes `*Test` rodam no `surefire` e `*IT` no `failsafe`; ambos rodam no `./mvnw verify`. Testes que exigem ffmpeg recebem `@Tag("ffmpeg")`.

## Forma de um teste

- Nome do método em inglês, descrevendo o comportamento esperado: `rejectsCommentBeyondVersionDuration`, `returnsNotFoundForNonMember`. Sem `test` no começo e sem `should`.
- Corpo em três blocos separados por linha em branco: preparação, ação e verificação. Sem comentários `// given`.
- AssertJ sempre. Para exceção de domínio: `assertThatThrownBy(...).isInstanceOfSatisfying(BusinessRuleException.class, error -> assertThat(error.code()).isEqualTo("..."))`.
- Uma regra por teste. Vários `assertThat` são OK se verificam o mesmo comportamento.
- Cada cenário da spec tocado pela tarefa vira pelo menos um teste. Cite o cenário no `@DisplayName` quando o nome do método não for óbvio.

## Dados de teste

- Construa entidades pelo builder real (que valida). Para objetos repetidos, use uma classe `*Fixtures` no pacote de teste com métodos estáticos (`TimedCommentFixtures.rootCommentAt(92_000)`).
- `Clock.fixed(...)` para tudo que depende de tempo.
- UUIDs fixos e legíveis nos testes de controller (`UUID.fromString("00000000-0000-0000-0000-000000000001")`).
- Áudio: gere o sinal no próprio teste (seno, metrônomo) em vez de versionar WAVs grandes. Fixtures binárias, só pequenas (< 200 KB) e em `src/test/resources/fixtures/`.

## Testcontainers

- `@ServiceConnection` quando o Spring Boot suporta (Postgres, RabbitMQ). Para o SeaweedFS, `GenericContainer` com `@DynamicPropertySource`.
- Imagens com tag fixa, iguais às do `docker/compose.yaml`.
- Containers declarados como beans `@ServiceConnection` numa `@TestConfiguration` (`PostgresTestConfiguration`) importada pelos testes. O Spring reaproveita o contexto em cache, então o container sobe uma vez por configuração.

## O que não fazer

- Não mocke o que você não possui (`RabbitTemplate`, `S3Client`). Teste o adapter com container e mocke o port.
- Não use `@SpringBootTest` para testar regra de negócio.
- Não use `Thread.sleep`. Para esperar algo assíncrono, use Awaitility com timeout.
- Não teste getters, records sem lógica ou configuração do framework.
