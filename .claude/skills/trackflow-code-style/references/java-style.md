# Estilo Java

## Tipos

| Para | Use | Exemplo |
|---|---|---|
| DTO HTTP, comando, view, evento, mensagem | `record` | `CreateCommentRequest`, `AddComment`, `CommentView`, `CommentAdded` |
| Entidade JPA | classe com Lombok | `TimedComment` |
| Valor com regra | `record` com construtor compacto que valida | `TimeRange(long startMs, @Nullable Long endMs)` |
| Conjunto fechado de variantes | `sealed interface` + records, consumido com `switch` | resultado de processamento |
| Constantes de um contrato | `final class` com construtor privado | `Topology` |

Records validam no construtor compacto e lançam `IllegalArgumentException` para estado impossível. Erros que o usuário pode causar viram Bean Validation no `*Request` ou `BusinessRuleException` no domínio.

## Lombok (só entidades)

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido pelo JPA
public class TimedComment {

    @Builder
    private TimedComment(...) { /* valida e atribui */ }

    public void resolve(UUID resolvedBy, Instant at) { /* muda estado */ }
}
```

- `@Builder` vai no **construtor privado**, nunca na classe. Assim todo objeto criado pelo builder passa pela validação.
- Nada de `@Setter`, `@Data`, `@Value`, `@EqualsAndHashCode` ou `@ToString`. O `lombok.config` (exemplo em `examples/lombok.config`) transforma todos eles em erro de compilação.
- Serviços, controllers e configs **não** usam Lombok: escreva o construtor. Fica explícito o que a classe precisa, e o construtor longo denuncia classe com responsabilidades demais.

## Injeção e visibilidade

- Construtor único, campos `private final`, sem `@Autowired`.
- Classes são package-private por padrão. Ficam `public` só quando outro pacote precisa delas: tipos da raiz do módulo, serviços usados pelo `web`, entidades e repositórios usados pelo `application`.
- `@ConfigurationProperties` em records, com prefixo `trackflow.<módulo>` e valores padrão no `application.yml`:

```java
@ConfigurationProperties("trackflow.version.upload")
record UploadProperties(DataSize maxFileSize, DataSize maxAbletonFileSize, Duration urlTtl) {}
```

## Nulos e Optional

- Públicos nunca retornam `null`. Busca que pode não achar retorna `Optional<T>`; coleção vazia, nunca `null`.
- `Optional` não vai em campo, parâmetro nem record de request.
- Parâmetro, campo ou componente que aceita `null` recebe `@Nullable` (`org.jspecify.annotations.Nullable`). Sem anotação significa não nulo.
- Prefira `orElseThrow(() -> new NotFoundException(...))` a `isPresent()` + `get()`.

## Nomes

- Código, logs e commits em inglês. Documentação (README, OpenSpec) em pt-BR.
- Serviço: `<Agregado>Service` (`TimedCommentService`). Controller: `<Agregado>Controller`.
- Request: `<Ação><Coisa>Request` (`CreateCommentRequest`). Response: `<Coisa>Response`.
- Comando: verbo + substantivo (`AddComment`). Evento: substantivo + particípio (`CommentAdded`, `VersionConfirmed`).
- Exceção: o problema (`InvalidMediaException`). Booleanos: `is`/`has`/`can`.
- Códigos de erro: kebab-case (`version-not-ready`).
- Unidades no nome quando o tipo não diz: `positionMs`, `sizeBytes`, `loudnessLufs`.

## Fluxo de controle

- `switch` expression com pattern matching para tipos sealed e enums. Sem `default` quando a lista é exaustiva, para o compilador avisar de caso novo.
- Stream para transformar coleções; `for` quando há efeito colateral ou saída antecipada.
- Retorne coleções imutáveis (`.toList()`, `List.copyOf`).
- Guard clauses no início do método em vez de `if` aninhado.
- `var` só quando o tipo é óbvio pelo lado direito (`var comment = TimedComment.builder()...build();`).

## Comentários e logs

- Comentário explica **por quê**, nunca o quê. Sem código comentado.
- Javadoc só na API pública dos módulos (tipos da raiz) e em algoritmos (`BpmEstimator`).
- SLF4J com placeholders e chave=valor: `log.info("Version confirmed versionId={} number={}", id, number)`.
- `INFO` para mudança de estado de negócio, `WARN` para algo recuperável e inesperado, `ERROR` só quando alguém precisa agir.
- Nunca logue tokens, URLs pré-assinadas, e-mails ou o corpo de comentários.

## Exceções

- Domínio: subclasses de `DomainException` (unchecked) de `shared.error`.
- Não capture `Exception` genérica, exceto em fronteiras (listener de mensagem, processo externo). Nelas, classifique e relance ou converta em resultado de falha.
- Nunca engula exceção: ou trate de verdade, ou propague com contexto (`new IllegalStateException("ffprobe failed for versionId=" + id, e)`).
