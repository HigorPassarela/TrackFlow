# Spec Delta

## Purpose

Transforma cada arquivo enviado em algo que dá para ouvir e analisar: valida o conteúdo, extrai áudio de vídeo, gera preview e forma de onda, mede a faixa e avisa os membros do andamento.

## ADDED Requirements

### Requirement: Processamento assíncrono
O sistema SHALL processar cada versão confirmada em segundo plano, sem bloquear a confirmação. O status da versão segue o fluxo "enviada", "em processamento" e, por fim, "pronta" ou "falhou".

#### Scenario: Fluxo de sucesso
- **WHEN** uma versão WAV válida é confirmada
- **THEN** a confirmação responde imediatamente e a versão chega a "pronta" com preview, forma de onda e métricas

#### Scenario: Status visível durante o processamento
- **WHEN** um membro consulta uma versão cujo processamento começou e ainda não terminou
- **THEN** a versão aparece com status "em processamento"

### Requirement: Validação pelo conteúdo do arquivo
O sistema SHALL validar o arquivo pelo conteúdo real, não pela extensão nem pelo tipo declarado. Arquivos sem faixa de áudio ou ilegíveis MUST levar a versão a "falhou" com um motivo legível.

#### Scenario: Arquivo corrompido
- **WHEN** o arquivo enviado como WAV não pode ser decodificado
- **THEN** a versão vai para "falhou" com o motivo "arquivo ilegível"

#### Scenario: Vídeo sem áudio
- **WHEN** o arquivo enviado é um vídeo sem nenhuma faixa de áudio
- **THEN** a versão vai para "falhou" com o motivo "nenhuma faixa de áudio encontrada"

### Requirement: Extração de áudio de vídeo
Quando a versão for um vídeo, o sistema SHALL extrair a primeira faixa de áudio e processá-la como se tivesse sido enviada como áudio.

#### Scenario: Gravação de tela
- **WHEN** um editor envia um MP4 de gravação de tela com áudio do Ableton
- **THEN** a versão fica pronta com preview e forma de onda gerados a partir do áudio do vídeo

### Requirement: Duração máxima
O sistema SHALL aceitar faixas de até 20 minutos. Arquivos mais longos MUST levar a versão a "falhou" com o motivo "duração acima de 20 minutos".

#### Scenario: Arquivo longo demais
- **WHEN** o arquivo enviado tem 45 minutos de áudio
- **THEN** a versão vai para "falhou" com o motivo de duração excedida

### Requirement: Preview para reprodução
O sistema SHALL gerar para cada versão pronta um preview comprimido, com no máximo 192 kbps e a mesma duração do original (tolerância de 50 ms), reproduzível nos navegadores atuais.

#### Scenario: Preview de um WAV
- **WHEN** uma versão WAV de 6 minutos é processada
- **THEN** o preview gerado tem 6 minutos e tamanho inferior a 10 MB

### Requirement: Dados de forma de onda
O sistema SHALL gerar os dados de forma de onda de cada versão pronta, com pelo menos 20 pontos por segundo de áudio e valores normalizados entre 0 e 1.

#### Scenario: Forma de onda de faixa com silêncio no início
- **WHEN** uma faixa começa com 2 segundos de silêncio absoluto
- **THEN** os pontos desses 2 segundos valem 0 e o ponto de maior amplitude da faixa vale 1

### Requirement: BPM estimado
O sistema SHALL estimar o BPM de cada versão, com uma casa decimal, entre 60 e 200, e SHALL apresentá-lo sempre identificado como estimativa.

#### Scenario: Metrônomo sintético
- **WHEN** a versão é um metrônomo sintético de 2 minutos em 124 BPM
- **THEN** o BPM estimado fica entre 123 e 125

#### Scenario: Faixa sem pulso
- **WHEN** a versão é um pad contínuo sem transientes
- **THEN** a versão fica pronta sem BPM estimado, em vez de exibir um valor inventado

### Requirement: Loudness e pico
O sistema SHALL medir a loudness integrada em LUFS e o true peak em dBTP de cada versão pronta, ambos com uma casa decimal.

#### Scenario: Faixa com pico acima de 0 dBTP
- **WHEN** uma versão tem true peak de +0,8 dBTP
- **THEN** a versão exibe o true peak +0,8 dBTP junto com a loudness integrada

### Requirement: Status em tempo real
O sistema SHALL avisar os membros conectados a um projeto, em até 2 segundos, sempre que o status de uma versão desse projeto mudar.

#### Scenario: Produtor acompanha o processamento
- **WHEN** um membro está conectado ao canal de eventos do projeto e uma versão passa para "pronta"
- **THEN** o membro recebe um evento com o identificador da versão e o novo status

#### Scenario: Não membro tenta se conectar
- **WHEN** um usuário que não é membro tenta abrir o canal de eventos do projeto
- **THEN** o sistema responde como recurso inexistente

### Requirement: Falhas temporárias e permanentes
O sistema SHALL tentar processar a versão até 3 vezes, com intervalos crescentes, quando a falha for temporária. Falhas causadas pelo próprio arquivo MUST levar a versão a "falhou" sem novas tentativas.

#### Scenario: Storage indisponível por instantes
- **WHEN** o storage fica indisponível na primeira tentativa e volta na segunda
- **THEN** a versão chega a "pronta" sem intervenção

#### Scenario: Falha persistente
- **WHEN** as 3 tentativas falham por erro temporário
- **THEN** a versão vai para "falhou" com o motivo "erro de processamento" e pode ser reprocessada

### Requirement: Resultados repetidos ou atrasados
O sistema MUST ignorar resultados de processamento que não correspondam à tentativa em andamento da versão, para que mensagens duplicadas ou atrasadas não alterem uma versão já concluída.

#### Scenario: Resultado duplicado
- **WHEN** o mesmo resultado de sucesso chega duas vezes
- **THEN** a versão permanece "pronta" com as métricas do primeiro resultado e os membros recebem um único aviso
