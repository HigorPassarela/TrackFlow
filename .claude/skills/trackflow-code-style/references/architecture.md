# Arquitetura: pacotes, módulos e ports

## Layout de um módulo da API

```
dev.higor.trackflow.api.comment          ← raiz = API pública do módulo
├── package-info.java                    ← @ApplicationModule(displayName = ...)
├── CommentAdded.java                    ← evento publicado (record)
├── web/                                 ← controllers + *Request/*Response
├── application/                         ← serviços (casos de uso), comandos, views, ports
├── domain/                              ← entidades, enums, regras puras, repositórios Spring Data
└── adapter/                             ← só se houver port: implementação de infraestrutura
```

- **Raiz**: só o que outro módulo pode usar, como interfaces de consulta e comando (`ProjectAccess`, `TrackVersions`), records de retorno, eventos e enums compartilhados (`ProjectRole`). Se uma classe na raiz não é usada por nenhum outro módulo, ela não deveria estar lá.
- **`web`**: depende de `application` e, para mapear respostas, das views de `application`. Não chama repositórios.
- **`application`**: orquestra. Checa permissão, carrega entidades, chama métodos de domínio, salva, publica eventos. Abre e fecha transações.
- **`domain`**: regras que não precisam de Spring para serem testadas. Entidades com comportamento, value objects, cálculos puros (`AbletonDiff`), repositórios Spring Data.
- **`adapter`**: S3, RabbitMQ, ffmpeg. Implementa um port declarado em `application` (ou na raiz, quando outros módulos usam o port, como `storage.ObjectStorage`).

A implementação de uma interface pública da raiz fica em `application` e é package-private quando possível (exemplo: `class ProjectAccessService implements ProjectAccess`).

## Dependências entre módulos

| Módulo | Pode depender de |
|---|---|
| `shared` | nada |
| `identity` | shared |
| `project` | identity, shared |
| `storage` | shared |
| `version` | project, storage, identity, shared |
| `ableton` | project, identity, shared |
| `comment` | version, project, identity, shared |
| `processing` | version, ableton, shared |

- Proibido: ciclos, acesso a subpacote interno de outro módulo e relacionamento JPA entre entidades de módulos diferentes.
- Para precisar de algo de outro módulo: (1) existe método na API pública dele? use; (2) não existe? adicione na raiz dele uma interface ou método pequeno e específico; (3) a informação só precisa chegar depois? escute um evento.
- Se a dependência criaria um ciclo, a responsabilidade está no módulo errado. Mova-a em vez de contornar com evento. Foi por isso que `collaboration` virou parte de `project`.

O `ModularityTest` verifica tudo isso. Não desligue regras para fazer o teste passar.

## Quando criar um port

Crie um port (interface em `application` ou na raiz) **apenas** quando pelo menos um destes for verdade:

1. A implementação muda entre ambientes (SeaweedFS local × S3).
2. O teste precisa de um fake para não depender de processo externo (ffmpeg, broker).
3. A chamada atravessa a rede ou o sistema operacional.

Repositório Spring Data não ganha port: ele já é uma interface, e o teste usa Postgres real.

## Módulo `shared`

Módulo aberto (`@ApplicationModule(type = Type.OPEN)`) com o que todo módulo usa: `shared.error`, `shared.web` (paginação, configuração MVC), `shared.time` (bean `Clock`) e `shared.security` (`TokenGenerator`). Ele não pode depender de nenhum outro módulo. Se algo em `shared` começa a conhecer projeto, versão ou usuário, não pertence a `shared`.

## Worker e contratos

- `trackflow-contracts`: só records e constantes (nomes de exchanges e routing keys). Sem Spring, sem Lombok. Mudança em contrato é sempre compatível para trás: adicione campo com valor padrão tratado, nunca renomeie.
- `trackflow-worker`: pacotes por capacidade (`audio`, `ableton`, `messaging`, `storage`). Processos externos ficam atrás de ports (`MediaProbe`, `Transcoder`, `LoudnessMeter`) com implementação `Ffmpeg*`.
