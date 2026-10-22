# API REST

## Controller

- Package-private, em `<módulo>.web`, um por agregado. `@RequestMapping` com o caminho completo do recurso (`/api/projects/{projectId}/versions/{versionId}/comments`).
- Recebe `AuthenticatedUser` como parâmetro (resolvido por `identity`). Nunca usa `SecurityContextHolder` nem `OAuth2User`.
- Faz três coisas: converte o `*Request` em comando, chama **um** método de serviço e converte o resultado em `*Response`. Lógica além disso vai para o serviço.
- `POST` que cria retorna `201` com o recurso no corpo (`@ResponseStatus(CREATED)`). O header `Location` (`ResponseEntity.created(uri)`) só entra quando existe um `GET` para o recurso criado. `PATCH` retorna `200` com o recurso; ações sem retorno útil (`resolve`) e `DELETE` retornam `204`.
- Ações que não são CRUD viram sub-recurso com verbo: `POST .../comments/{commentId}/resolve`.

## Request e Response

- `record` público em `web`, com Bean Validation nos componentes (`@NotBlank`, `@Size`, `@PositiveOrZero`).
- Validação que envolve dois campos fica num método `@AssertTrue` do próprio record (por exemplo, fim do trecho depois do início) ou no domínio, se depender de estado.
- `*Response.from(view)` é o único lugar de mapeamento. Sem MapStruct: o mapeamento é explícito e curto.
- Datas saem como `Instant` (ISO-8601 em UTC). Durações e posições, em milissegundos com o sufixo `Ms`.
- Campos sem valor saem como `null` em vez de serem omitidos, para o contrato ser estável. Coleções vazias saem `[]`.

## Erros

Formato RFC 9457, gerado só pelo `GlobalExceptionHandler`:

```json
{
  "type": "https://trackflow.dev/errors/version-not-ready",
  "title": "Version not ready",
  "status": 409,
  "detail": "Version 4 is still processing",
  "instance": "/api/projects/…/comments",
  "code": "version-not-ready"
}
```

| Situação | Exceção | Status |
|---|---|---|
| Recurso não existe ou usuário não é membro | `NotFoundException` | 404 |
| Membro sem papel suficiente | `PermissionDeniedException` | 403 |
| Regra de estado (arquivado, não pronto, convite expirado) | `BusinessRuleException` | 409 |
| Bean Validation | (automático) | 400 com `errors: [{field, message}]` |
| Sem sessão | (Spring Security) | 401 |

`detail` é para humanos e pode mudar. Clientes decidem pelo `code`.

## Paginação

Parâmetros `page` (a partir de 0) e `size` (padrão 20, máximo 100). A resposta é `PageResponse<T>(List<T> items, int page, int size, long totalItems)`, de `shared.web`. Não exponha o `Page` do Spring Data, porque o JSON dele não é contrato estável.

## Segurança em todo endpoint

1. O serviço chama `ProjectAccess.requireRole(...)` (ou `requireRoleOnActiveProject` para escrita) **antes** de qualquer leitura.
2. A busca do recurso usa o `projectId` da URL: `findByIdAndProjectId(commentId, projectId)`. Assim, um id válido de outro projeto vira 404.
3. Tokens de convite e links nunca aparecem em respostas de listagem.
