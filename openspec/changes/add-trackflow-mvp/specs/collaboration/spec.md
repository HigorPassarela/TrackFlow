# Spec Delta

## Purpose

Controla quem participa de cada projeto e com qual papel, como novas pessoas entram por convite e como uma versão pode ser ouvida por quem não tem conta.

## ADDED Requirements

### Requirement: Papéis no projeto
Cada membro SHALL ter exatamente um papel no projeto: dono, editor ou ouvinte. Cada projeto MUST ter exatamente um dono.

#### Scenario: Permissões por papel
- **WHEN** o sistema avalia uma operação num projeto
- **THEN** o dono pode tudo, o editor envia e edita versões e comenta, e o ouvinte ouve e comenta

#### Scenario: Papel insuficiente
- **WHEN** um ouvinte tenta uma operação que exige papel de editor
- **THEN** o sistema rejeita a operação com erro de permissão

### Requirement: Convite por link
O sistema SHALL permitir que o dono gere um link de convite para o papel de editor ou de ouvinte. O link vale por 7 dias e pode ser usado uma única vez.

#### Scenario: Dono gera convite
- **WHEN** o dono gera um convite para o papel de editor
- **THEN** o sistema retorna um link de convite com a data de expiração

#### Scenario: Editor tenta gerar convite
- **WHEN** um editor tenta gerar um convite
- **THEN** o sistema rejeita a operação com erro de permissão

### Requirement: Aceitar convite
O sistema SHALL adicionar ao projeto, com o papel do convite, o usuário autenticado que aceitar um convite válido, e SHALL marcar o convite como usado.

#### Scenario: Convite válido
- **WHEN** um usuário autenticado que não é membro aceita um convite válido de ouvinte
- **THEN** o usuário passa a ser ouvinte do projeto e o convite não pode mais ser usado

#### Scenario: Convite expirado
- **WHEN** um usuário tenta aceitar um convite emitido há mais de 7 dias
- **THEN** o sistema recusa informando que o convite expirou

#### Scenario: Convite já usado
- **WHEN** um segundo usuário tenta aceitar um convite que já foi aceito
- **THEN** o sistema recusa informando que o convite não está mais disponível

#### Scenario: Usuário já é membro
- **WHEN** um membro do projeto tenta aceitar um convite do mesmo projeto
- **THEN** o sistema recusa informando que o usuário já é membro, sem alterar o papel atual nem consumir o convite

### Requirement: Revogar convite
O sistema SHALL permitir que o dono revogue um convite ainda não usado. Um convite revogado MUST NOT poder ser aceito.

#### Scenario: Convite revogado
- **WHEN** alguém tenta aceitar um convite revogado
- **THEN** o sistema recusa informando que o convite não está mais disponível

### Requirement: Listar convites pendentes
O sistema SHALL permitir que o dono liste os convites do projeto que ainda não foram usados, revogados nem expiraram.

#### Scenario: Dono lista convites
- **WHEN** o dono lista os convites de um projeto com um convite pendente e um expirado
- **THEN** o sistema retorna apenas o convite pendente

### Requirement: Gerenciar membros
O sistema SHALL permitir que qualquer membro liste os membros do projeto com seus papéis. O dono pode alterar o papel de editores e ouvintes e removê-los. O dono MUST NOT conseguir remover a si mesmo nem rebaixar o próprio papel.

#### Scenario: Promover ouvinte
- **WHEN** o dono altera o papel de um ouvinte para editor
- **THEN** o membro passa a ter as permissões de editor na próxima requisição

#### Scenario: Remover membro
- **WHEN** o dono remove um editor
- **THEN** o ex-membro passa a receber o projeto como inexistente

#### Scenario: Dono tenta se remover
- **WHEN** o dono tenta remover a si mesmo do projeto
- **THEN** o sistema rejeita a operação informando que o projeto precisa de um dono

### Requirement: Sair do projeto
O sistema SHALL permitir que editores e ouvintes saiam de um projeto por conta própria.

#### Scenario: Ouvinte sai do projeto
- **WHEN** um ouvinte sai do projeto
- **THEN** o projeto deixa de aparecer na lista de projetos desse usuário

### Requirement: Link de audição
O sistema SHALL permitir que dono e editores criem um link de audição para uma versão pronta, com validade de 1 a 30 dias (padrão de 7). O link dá acesso somente a ouvir essa versão, sem login.

#### Scenario: Visitante abre link válido
- **WHEN** um visitante sem conta abre um link de audição válido
- **THEN** o sistema retorna título do projeto, número da versão, nome artístico do autor, endereço temporário de reprodução e dados de forma de onda, sem comentários nem outras versões

#### Scenario: Link expirado
- **WHEN** um visitante abre um link de audição vencido
- **THEN** o sistema responde como recurso inexistente

#### Scenario: Versão ainda não pronta
- **WHEN** um editor tenta criar link de audição para uma versão que ainda está em processamento
- **THEN** o sistema rejeita informando que a versão ainda não está pronta

### Requirement: Revogar link de audição
O sistema SHALL permitir que o dono e quem criou o link o revoguem a qualquer momento. Depois disso, o link MUST se comportar como inexistente.

#### Scenario: Link revogado
- **WHEN** um visitante abre um link de audição revogado
- **THEN** o sistema responde como recurso inexistente

### Requirement: Segredos de links não recuperáveis
Os tokens de convite e de link de audição MUST ser imprevisíveis e MUST NOT ser armazenados de forma que permita recuperá-los. Só quem gerou o link o recebe, e uma única vez.

#### Scenario: Consulta posterior de convite
- **WHEN** o dono lista os convites pendentes do projeto
- **THEN** o sistema mostra papel, criador e expiração de cada convite, sem exibir o link
