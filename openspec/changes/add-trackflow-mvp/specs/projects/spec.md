# Spec Delta

## Purpose

Organiza o trabalho em projetos, um por faixa, que agrupam versões, colaboradores e comentários e podem ser arquivados quando a faixa termina.

## ADDED Requirements

### Requirement: Criar projeto
O sistema SHALL permitir que qualquer usuário autenticado crie um projeto com título obrigatório (1 a 120 caracteres), descrição opcional (até 2000 caracteres) e gênero opcional. Quem cria se torna o dono do projeto.

#### Scenario: Criação válida
- **WHEN** um usuário cria o projeto "Ibiza Vibes" com gênero "tech house"
- **THEN** o sistema cria o projeto e registra o usuário como dono

#### Scenario: Título ausente
- **WHEN** um usuário tenta criar um projeto com título vazio
- **THEN** o sistema rejeita a criação com erro de validação que indica o campo título

### Requirement: Listar meus projetos
O sistema SHALL listar, de forma paginada, os projetos em que o usuário é membro, com o papel dele em cada um, do mais recentemente ativo para o menos. Contam como atividade a edição do projeto, a confirmação de versão e novos comentários. Projetos arquivados ficam fora da lista, a menos que sejam pedidos.

#### Scenario: Usuário com projetos próprios e de terceiros
- **WHEN** um usuário que é dono de um projeto e ouvinte de outro lista seus projetos
- **THEN** o sistema retorna os dois projetos, cada um com o papel do usuário

#### Scenario: Nova versão sobe o projeto na lista
- **WHEN** uma versão é confirmada no projeto que estava em último lugar na lista do usuário
- **THEN** esse projeto passa a aparecer em primeiro lugar

#### Scenario: Projeto arquivado
- **WHEN** um usuário lista seus projetos sem pedir os arquivados
- **THEN** o sistema não inclui projetos arquivados no resultado

### Requirement: Consultar projeto
O sistema SHALL permitir que membros consultem os detalhes de um projeto. Para quem não é membro, o projeto MUST se comportar como inexistente.

#### Scenario: Membro consulta projeto
- **WHEN** um ouvinte do projeto consulta seus detalhes
- **THEN** o sistema retorna título, descrição, gênero, nome artístico do dono e o papel do ouvinte

#### Scenario: Não membro consulta projeto
- **WHEN** um usuário que não é membro consulta o projeto
- **THEN** o sistema responde como recurso inexistente

### Requirement: Editar projeto
O sistema SHALL permitir que dono e editores alterem título, descrição e gênero do projeto, com as mesmas regras de validação da criação.

#### Scenario: Editor altera descrição
- **WHEN** um editor altera a descrição do projeto
- **THEN** o sistema salva a nova descrição

#### Scenario: Ouvinte tenta editar
- **WHEN** um ouvinte tenta alterar o título do projeto
- **THEN** o sistema rejeita a alteração com erro de permissão

### Requirement: Arquivar projeto
O sistema SHALL permitir que o dono arquive e desarquive o projeto. Um projeto arquivado MUST ficar somente leitura: não aceita novas versões, comentários, convites nem edições.

#### Scenario: Dono arquiva
- **WHEN** o dono arquiva o projeto
- **THEN** o projeto continua consultável pelos membros e passa a recusar alterações

#### Scenario: Envio de versão em projeto arquivado
- **WHEN** um editor solicita o envio de uma nova versão num projeto arquivado
- **THEN** o sistema rejeita a solicitação informando que o projeto está arquivado

#### Scenario: Editor tenta arquivar
- **WHEN** um editor tenta arquivar o projeto
- **THEN** o sistema rejeita a operação com erro de permissão
