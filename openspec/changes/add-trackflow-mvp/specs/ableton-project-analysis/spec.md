# Spec Delta

## Purpose

Lê o projeto do Ableton Live anexado a uma versão para mostrar como a faixa foi construída e o que mudou entre duas versões, sem que ninguém precise abrir o Live.

## ADDED Requirements

### Requirement: Resumo do projeto do Ableton
Para cada versão com projeto do Ableton anexado, o sistema SHALL extrair em segundo plano a versão do Live, o tempo, a fórmula de compasso, as tracks (tipo e nome), os devices de cada track e os plugins de terceiros usados.

#### Scenario: Projeto de tech house
- **WHEN** uma versão é confirmada com um `.als` de 126 BPM, com tracks "Kick", "Bass" e "Vocal Chop" e o plugin Serum na track "Bass"
- **THEN** o resumo mostra 126 BPM, as três tracks com seus tipos e Serum entre os plugins da track "Bass"

#### Scenario: Membro consulta resumo
- **WHEN** um ouvinte consulta o resumo do projeto do Ableton de uma versão
- **THEN** o sistema retorna o resumo, mesmo que o ouvinte não possa baixar o arquivo `.als`

### Requirement: Versões do Live suportadas
O sistema SHALL analisar projetos salvos no Ableton Live 11 e 12. Projetos de outras versões SHALL ser analisados em melhor esforço e marcados como versão não verificada.

#### Scenario: Projeto do Live 12
- **WHEN** o projeto anexado foi salvo no Live 12
- **THEN** o resumo identifica a versão do Live e não traz aviso de versão não verificada

### Requirement: Leitura segura de arquivos de projeto
O sistema MUST tratar o `.als` como entrada não confiável: não segue referências externas contidas no arquivo e interrompe a leitura se o conteúdo descompactado passar de 300 MB.

#### Scenario: Arquivo com referência externa
- **WHEN** o `.als` contém uma declaração que aponta para um arquivo ou endereço externo
- **THEN** a análise falha com o motivo "arquivo de projeto inválido" e nada externo é acessado

#### Scenario: Bomba de descompressão
- **WHEN** o `.als` tem 1 MB compactado e passaria de 300 MB descompactado
- **THEN** a análise é interrompida e falha com o motivo "arquivo de projeto grande demais"

### Requirement: Falha na análise não afeta a versão
Uma falha na análise do projeto do Ableton MUST NOT impedir a versão de ficar pronta. A análise tem status próprio, e o motivo da falha fica visível aos membros.

#### Scenario: Projeto corrompido
- **WHEN** o áudio da versão é válido e o `.als` anexado está corrompido
- **THEN** a versão fica pronta e a análise do projeto aparece como falha com o motivo

### Requirement: Diff entre versões
O sistema SHALL comparar os projetos do Ableton de duas versões do mesmo projeto e listar mudança de tempo, tracks adicionadas, removidas e renomeadas, devices adicionados e removidos por track e plugins adicionados e removidos.

#### Scenario: Evolução de v3 para v4
- **WHEN** a v4 adiciona as tracks "Vocal Chop" e "Riser", remove o Serum da track "Bass" e muda o tempo de 125 para 126 BPM
- **THEN** o diff lista as duas tracks adicionadas, o Serum removido da track "Bass" e o tempo de 125 para 126

#### Scenario: Track renomeada
- **WHEN** a mesma track se chama "Bass" na v3 e "Sub Bass" na v4
- **THEN** o diff mostra uma renomeação de "Bass" para "Sub Bass", não uma remoção seguida de adição

#### Scenario: Versão sem projeto analisado
- **WHEN** um membro pede o diff entre uma versão com análise concluída e outra sem projeto anexado
- **THEN** o sistema recusa informando qual versão não tem análise disponível

#### Scenario: Versões de projetos diferentes
- **WHEN** um membro pede o diff entre versões de dois projetos diferentes
- **THEN** o sistema responde como recurso inexistente
