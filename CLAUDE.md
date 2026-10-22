# Trackflow

Plataforma de colaboração para produtores musicais: cada projeto é uma faixa, cada versão é um bounce processado de forma assíncrona e comentado em pontos exatos da forma de onda. É projeto de portfólio, então clareza, testes e decisões documentadas valem tanto quanto as features.

## Onde está cada coisa

- `openspec/`: specs e changes (fluxo OpenSpec). A change em andamento é `openspec/changes/add-trackflow-mvp/`.
- `.claude/skills/trackflow-code-style/`: **como escrever código aqui**. Carregue essa skill antes de criar, alterar ou revisar qualquer arquivo Java, teste ou migration.
- `trackflow-api/`: Spring Boot (MVC + Spring Modulith). Módulos em `dev.higor.trackflow.api.*`.
- `trackflow-worker/`: processamento de áudio (ffmpeg) e de projetos do Ableton.
- `trackflow-contracts/`: records das mensagens entre API e worker.
- `docker/compose.yaml`: PostgreSQL, RabbitMQ e SeaweedFS para desenvolvimento.

## Fluxo de trabalho

1. Mudança de comportamento começa no OpenSpec: `/opsx:propose` (ou `/opsx:explore` para pensar antes).
2. Implementação com `/opsx:apply`, seguindo `tasks.md` em ordem e marcando cada tarefa só depois da verificação descrita nela.
3. Se uma spec estiver errada ou incompleta durante a implementação, pare e proponha a correção da change. Não implemente algo diferente do que a spec diz.
4. Ao terminar um grupo de tarefas: `./mvnw -B verify` precisa passar.

## Comandos

```bash
docker compose -f docker/compose.yaml up -d          # infraestrutura local
./mvnw -B verify                                     # build + testes + Spotless + ModularityTest
./mvnw spotless:apply                                # formata o código
./mvnw -pl trackflow-api spring-boot:run -Dspring-boot.run.profiles=local
openspec validate add-trackflow-mvp --strict         # valida a change
```

## Regras que não podem ser quebradas

- Nunca importe classes de subpacotes internos de outro módulo; use a API pública da raiz do módulo ou eventos.
- Toda operação em recurso de projeto checa o papel com `ProjectAccess` antes de ler qualquer coisa. Não membro recebe 404.
- Nunca edite uma migration Flyway já commitada; crie a próxima.
- Nunca commite segredos. Variáveis novas vão para `.env.example` com valor de exemplo.
- Não desligue regras do `ModularityTest`, do Spotless ou do `lombok.config` para o build passar.
- Código, logs e mensagens de commit em inglês; documentação em pt-BR.
