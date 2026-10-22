# Spec Delta

## Purpose

Identifica quem usa o Trackflow: login social, criação automática da conta no primeiro acesso, perfil com nome artístico e encerramento de sessão.

## ADDED Requirements

### Requirement: Login social
O sistema SHALL permitir login por GitHub ou Google e MUST NOT armazenar senhas de usuários.

#### Scenario: Login com GitHub
- **WHEN** um visitante conclui a autorização no GitHub
- **THEN** o sistema inicia uma sessão autenticada para esse usuário

#### Scenario: Autorização negada no provedor
- **WHEN** o visitante cancela a autorização no provedor
- **THEN** o sistema não inicia sessão e informa que o login não foi concluído

### Requirement: Conta criada no primeiro acesso
O sistema SHALL criar a conta do usuário no primeiro login, identificando-o pelo par provedor e identificador do provedor, e SHALL reutilizar essa conta nos logins seguintes.

#### Scenario: Primeiro login
- **WHEN** um usuário faz login pela primeira vez com uma conta do GitHub
- **THEN** o sistema cria a conta com o nome exibido e o e-mail informados pelo provedor

#### Scenario: Login recorrente
- **WHEN** o mesmo usuário faz login novamente pelo mesmo provedor
- **THEN** o sistema usa a conta existente e não cria uma nova

#### Scenario: Mesmo e-mail em provedores diferentes
- **WHEN** um usuário que entrou pelo GitHub faz login pelo Google com o mesmo e-mail
- **THEN** o sistema cria uma conta separada, sem vincular contas automaticamente pelo e-mail

### Requirement: Perfil do usuário
O sistema SHALL permitir que o usuário autenticado consulte o próprio perfil e altere seu nome artístico, com 2 a 60 caracteres.

#### Scenario: Alterar nome artístico
- **WHEN** o usuário altera o nome artístico para "VERXT"
- **THEN** o perfil passa a exibir "VERXT" em projetos, versões e comentários

#### Scenario: Nome artístico inválido
- **WHEN** o usuário envia um nome artístico com 1 caractere
- **THEN** o sistema rejeita a alteração com erro de validação que indica o campo

### Requirement: Acesso exige autenticação
Toda operação do sistema MUST exigir sessão autenticada, exceto o login, a página de demonstração, a verificação de saúde e o acesso por link de audição.

#### Scenario: Requisição sem sessão
- **WHEN** uma requisição sem sessão tenta listar projetos
- **THEN** o sistema responde com erro de não autenticado, sem dados do recurso

### Requirement: Proteção contra requisições forjadas
O sistema MUST rejeitar requisições que alteram estado e que não tragam o token anti-falsificação emitido para a sessão.

#### Scenario: Alteração sem token
- **WHEN** uma requisição autenticada tenta criar um projeto sem o token anti-falsificação
- **THEN** o sistema rejeita a requisição e não cria o projeto

### Requirement: Encerrar sessão
O sistema SHALL permitir que o usuário encerre a própria sessão, invalidando-a no servidor.

#### Scenario: Logout
- **WHEN** o usuário encerra a sessão
- **THEN** requisições seguintes com o mesmo cookie de sessão são tratadas como não autenticadas

### Requirement: Login de desenvolvimento restrito ao ambiente local
O sistema SHALL oferecer, apenas no ambiente local, um login de desenvolvimento sem provedor externo e MUST NOT expô-lo em nenhum outro ambiente.

#### Scenario: Ambiente local
- **WHEN** a aplicação roda no ambiente local e o desenvolvedor usa o login de desenvolvimento com o nome "produtor-a"
- **THEN** o sistema inicia uma sessão para uma conta de teste com esse nome

#### Scenario: Outro ambiente
- **WHEN** a aplicação roda fora do ambiente local e alguém chama o login de desenvolvimento
- **THEN** o sistema responde como recurso inexistente
