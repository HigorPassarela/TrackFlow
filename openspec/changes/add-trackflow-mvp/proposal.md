# Proposal: MVP do Trackflow

## Why

Produtores que fazem música juntos trocam bounces por WhatsApp e Google Drive, perdem o histórico de versões e dão feedback do tipo "lá pelo meio tem um problema no kick", sem saber em qual versão nem em qual segundo. O Trackflow resolve isso com um "GitHub de faixas" e é também o projeto de portfólio que mostra upload de arquivos grandes, processamento assíncrono de áudio e um monólito modular bem testado.

## What Changes

- Login com GitHub ou Google e conta criada no primeiro acesso.
- Projetos (um por faixa) com colaboradores em três papéis: dono, editor e ouvinte, convidados por link.
- Versões numeradas de cada faixa, enviadas direto para o storage por URL pré-assinada. Aceita áudio e também vídeo de gravação de tela, do qual o áudio é extraído.
- Processamento assíncrono de cada versão: validação do arquivo, preview leve para streaming, dados de forma de onda, duração, BPM estimado e loudness. O status chega em tempo real.
- Comentários marcados no tempo (ponto ou trecho da faixa), com respostas e marcação de resolvido.
- Links de audição com validade, para ouvir uma versão sem precisar de conta.
- Leitura opcional do projeto do Ableton Live (`.als`) anexado à versão: BPM, tracks, devices e plugins, e o diff entre duas versões.
- Repositório com API e worker separados, ambiente local com um único `docker compose up`, CI no GitHub Actions e página de demonstração mínima.

## Capabilities

### New Capabilities

- `identity`: contas de usuário, login social, perfil com nome artístico e sessão.
- `projects`: criação, consulta, edição e arquivamento de projetos (faixas).
- `collaboration`: papéis no projeto, convites por link, gestão de membros e links de audição públicos com validade.
- `track-versions`: envio de versões, numeração, notas, histórico, reprodução e download.
- `audio-processing`: validação, extração de áudio de vídeo, preview, forma de onda, métricas de áudio e status em tempo real.
- `timed-comments`: comentários em pontos ou trechos de uma versão, respostas e resolução.
- `ableton-project-analysis`: leitura segura de arquivos `.als`, resumo do projeto e diff entre versões.

### Modified Capabilities

Nenhuma. O repositório ainda não tem specs.

## Out of Scope

- Montagem de setlists para DJs com IA (Spring AI + LiteLLM): entra na change seguinte, `add-dj-setlists`. Ela vai modificar `track-versions` para marcar faixas como liberadas para DJs e acrescentar a detecção de tom musical.
- Player de stems com mute e solo, e comparação A/B com volume equalizado.
- Cadastro dos plugins de cada colaborador e aviso de "plugins que você não tem".
- Exclusão definitiva de projetos e versões (o MVP só arquiva).
- Notificações por e-mail e push.
- Frontend completo. O MVP traz só uma página de demonstração.

## Impact

- Repositório novo `trackflow-service` com três módulos Maven: `trackflow-api`, `trackflow-worker` e `trackflow-contracts`.
- Infraestrutura local: PostgreSQL, RabbitMQ e storage compatível com S3, via `docker/compose.yaml`.
- Integrações externas: provedores OAuth2 (GitHub e Google). Credenciais ficam apenas em variáveis de ambiente.
- Dependência de sistema no worker: ffmpeg e ffprobe, empacotados na imagem Docker.
