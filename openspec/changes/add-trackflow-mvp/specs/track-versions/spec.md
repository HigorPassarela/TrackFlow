# Spec Delta

## Purpose

Mantém o histórico de versões de cada faixa: envio de arquivos grandes direto para o storage, numeração sequencial, notas, reprodução e download.

## ADDED Requirements

### Requirement: Solicitar envio de versão
O sistema SHALL permitir que dono e editores solicitem o envio de uma versão informando nome, tipo e tamanho do arquivo. A resposta traz um endereço temporário de envio, válido por 15 minutos e restrito a esse tamanho e tipo.

#### Scenario: Editor solicita envio de WAV
- **WHEN** um editor solicita o envio de um arquivo WAV de 120 MB
- **THEN** o sistema cria a versão com status "aguardando envio" e retorna o endereço de envio com sua expiração

#### Scenario: Ouvinte solicita envio
- **WHEN** um ouvinte solicita o envio de uma versão
- **THEN** o sistema rejeita a solicitação com erro de permissão

### Requirement: Formatos e tamanho aceitos
O sistema SHALL aceitar áudio WAV, AIFF, FLAC e MP3 e vídeo MP4, MOV, MKV e WEBM, com até 1 GB por arquivo. Solicitações fora disso MUST ser recusadas antes de qualquer envio.

#### Scenario: Arquivo grande demais
- **WHEN** um editor solicita o envio de um arquivo de 1,5 GB
- **THEN** o sistema recusa informando o limite de 1 GB

#### Scenario: Formato não suportado
- **WHEN** um editor solicita o envio de um arquivo do tipo OGG
- **THEN** o sistema recusa informando os formatos aceitos

### Requirement: Anexar projeto do Ableton
O sistema SHALL permitir anexar, na mesma solicitação de envio, um arquivo de projeto do Ableton Live (`.als`) de até 50 MB, com seu próprio endereço temporário de envio.

#### Scenario: Versão com projeto anexado
- **WHEN** um editor solicita o envio de um WAV junto com um arquivo `.als` de 2 MB
- **THEN** o sistema retorna um endereço de envio para cada arquivo

#### Scenario: Anexo com extensão errada
- **WHEN** um editor tenta anexar um arquivo `.zip` como projeto do Ableton
- **THEN** o sistema recusa informando que só aceita arquivos `.als`

### Requirement: Confirmar envio
O sistema SHALL confirmar uma versão quando o cliente avisar que o envio terminou, depois de verificar que cada arquivo existe no storage com o tamanho declarado. Ao confirmar, a versão recebe seu número e o status "enviada", e o processamento é disparado.

#### Scenario: Confirmação válida
- **WHEN** o editor confirma o envio de uma versão cujo arquivo está no storage com o tamanho declarado
- **THEN** a versão recebe o próximo número do projeto e passa para "enviada"

#### Scenario: Arquivo ausente ou divergente
- **WHEN** o editor confirma uma versão cujo arquivo não está no storage ou tem tamanho diferente do declarado
- **THEN** o sistema recusa a confirmação, mantém a versão em "aguardando envio" e não lhe atribui número

#### Scenario: Confirmação repetida
- **WHEN** o editor confirma novamente uma versão já confirmada
- **THEN** o sistema responde com a versão já confirmada, sem alterar número nem disparar novo processamento

### Requirement: Numeração sequencial
Versões confirmadas SHALL receber números sequenciais por projeto, começando em 1, sem repetição e sem lacunas, mesmo com confirmações simultâneas. Envios não confirmados MUST NOT consumir número.

#### Scenario: Confirmações simultâneas
- **WHEN** duas versões do mesmo projeto são confirmadas ao mesmo tempo e a última versão existente é a 3
- **THEN** uma recebe o número 4 e a outra o número 5

#### Scenario: Envio abandonado
- **WHEN** uma versão fica sem confirmação e a seguinte é confirmada
- **THEN** a confirmada recebe o número seguinte ao da última versão confirmada

### Requirement: Envio não confirmado expira
O sistema SHALL descartar, junto com os arquivos já enviados, as versões que permanecerem em "aguardando envio" por mais de 24 horas.

#### Scenario: Limpeza de envio abandonado
- **WHEN** uma versão está em "aguardando envio" há 25 horas
- **THEN** o sistema a remove e ela deixa de aparecer em qualquer consulta

### Requirement: Título e notas da versão
O sistema SHALL permitir que dono e editores definam e alterem o título (até 80 caracteres) e as notas (até 2000 caracteres) de uma versão, como "kick novo, sidechain no baixo".

#### Scenario: Editar notas
- **WHEN** um editor altera as notas da versão 4
- **THEN** as novas notas aparecem no histórico de versões

#### Scenario: Ouvinte tenta editar
- **WHEN** um ouvinte tenta alterar o título de uma versão
- **THEN** o sistema rejeita a alteração com erro de permissão

### Requirement: Histórico de versões
O sistema SHALL listar para os membros as versões confirmadas do projeto, da mais recente para a mais antiga, com número, título, autor, data, status e métricas disponíveis.

#### Scenario: Histórico com versão em processamento
- **WHEN** um membro lista as versões e a mais recente ainda está em processamento
- **THEN** a mais recente aparece primeiro com status "em processamento" e sem métricas

### Requirement: Ouvir versão
O sistema SHALL fornecer aos membros, para versões prontas, um endereço temporário de reprodução do preview válido por 1 hora, que aceite requisições parciais para permitir avançar e voltar na faixa.

#### Scenario: Membro ouve versão pronta
- **WHEN** um ouvinte pede para ouvir uma versão pronta
- **THEN** o sistema retorna o endereço de reprodução, o endereço dos dados de forma de onda e a expiração

#### Scenario: Versão não pronta
- **WHEN** um membro pede para ouvir uma versão em processamento
- **THEN** o sistema recusa informando que a versão ainda não está pronta

### Requirement: Baixar arquivo original
O sistema SHALL permitir que dono e editores baixem o arquivo original e o projeto do Ableton anexado. Ouvintes MUST NOT ter acesso aos originais.

#### Scenario: Editor baixa original
- **WHEN** um editor pede o arquivo original da versão 2
- **THEN** o sistema retorna um endereço temporário de download com o nome original do arquivo

#### Scenario: Ouvinte pede original
- **WHEN** um ouvinte pede o arquivo original de uma versão
- **THEN** o sistema rejeita com erro de permissão

### Requirement: Corrigir BPM
O sistema SHALL permitir que dono e editores informem o BPM correto de uma versão, entre 40 e 250. Depois disso, o BPM informado passa a valer no lugar do estimado, e ambos continuam visíveis.

#### Scenario: Estimativa errada
- **WHEN** a estimativa da versão foi 63 BPM e o editor informa 126
- **THEN** a versão passa a exibir 126 como BPM e 63 como estimativa

### Requirement: Reprocessar versão com falha
O sistema SHALL permitir que dono e editores solicitem novo processamento de uma versão com status "falhou". Para versões em qualquer outro status, a solicitação MUST ser recusada.

#### Scenario: Reprocessar falha
- **WHEN** um editor pede o reprocessamento de uma versão que falhou por erro temporário
- **THEN** a versão volta para "enviada" e o processamento é disparado de novo

#### Scenario: Reprocessar versão pronta
- **WHEN** um editor pede o reprocessamento de uma versão pronta
- **THEN** o sistema recusa informando que apenas versões com falha podem ser reprocessadas
