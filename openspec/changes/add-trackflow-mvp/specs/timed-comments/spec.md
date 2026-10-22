# Spec Delta

## Purpose

Permite dar feedback preso a um ponto ou trecho exato de uma versão, como "no 1:32 o kick embola com o baixo", com respostas e controle do que já foi resolvido.

## ADDED Requirements

### Requirement: Comentar ponto ou trecho
O sistema SHALL permitir que qualquer membro comente uma versão pronta, informando a posição em milissegundos e, opcionalmente, o fim de um trecho. O texto tem de 1 a 2000 caracteres, e as posições MUST estar dentro da duração da versão.

#### Scenario: Comentário pontual
- **WHEN** um ouvinte comenta "kick embolando com o baixo" na posição 92000 ms
- **THEN** o comentário é criado na posição 92000 ms com o nome artístico do autor

#### Scenario: Comentário de trecho
- **WHEN** um editor comenta o trecho de 60000 ms a 75000 ms
- **THEN** o comentário é criado com início e fim do trecho

#### Scenario: Posição fora da faixa
- **WHEN** um membro comenta na posição 400000 ms de uma versão de 360000 ms
- **THEN** o sistema rejeita o comentário informando a duração da versão

#### Scenario: Fim antes do início
- **WHEN** um membro informa um trecho com fim em 50000 ms e início em 60000 ms
- **THEN** o sistema rejeita o comentário com erro de validação

#### Scenario: Versão em processamento
- **WHEN** um membro tenta comentar uma versão que ainda não está pronta
- **THEN** o sistema recusa informando que a versão ainda não está pronta

#### Scenario: Não membro tenta comentar
- **WHEN** um usuário que não é membro tenta comentar uma versão do projeto
- **THEN** o sistema responde como recurso inexistente

### Requirement: Responder comentário
O sistema SHALL permitir que membros respondam a um comentário. Respostas não têm posição própria. Uma resposta a outra resposta MUST ser anexada ao fio do comentário original.

#### Scenario: Resposta direta
- **WHEN** um editor responde "corrigi na v5" a um comentário
- **THEN** a resposta aparece no fio desse comentário

#### Scenario: Resposta a uma resposta
- **WHEN** um membro responde a uma resposta
- **THEN** a nova resposta aparece no fio do comentário original, logo após as anteriores

### Requirement: Listar comentários de uma versão
O sistema SHALL listar para os membros os comentários de uma versão ordenados pela posição na faixa, cada um com suas respostas em ordem cronológica, e SHALL permitir filtrar apenas os não resolvidos.

#### Scenario: Ordem por posição
- **WHEN** existem comentários nas posições 120000 ms, 30000 ms e 92000 ms
- **THEN** a lista retorna 30000 ms, 92000 ms e 120000 ms, nessa ordem

#### Scenario: Filtrar não resolvidos
- **WHEN** um membro pede apenas os comentários não resolvidos
- **THEN** a lista omite os comentários resolvidos e as respostas deles

### Requirement: Editar comentário
O sistema SHALL permitir que apenas o autor edite o texto do próprio comentário ou resposta, que passa a ser exibido como editado.

#### Scenario: Autor edita
- **WHEN** o autor corrige o texto do próprio comentário
- **THEN** o comentário exibe o novo texto e a indicação de editado

#### Scenario: Outro membro tenta editar
- **WHEN** o dono do projeto tenta editar o texto do comentário de outra pessoa
- **THEN** o sistema rejeita com erro de permissão

### Requirement: Excluir comentário
O sistema SHALL permitir que o autor ou o dono do projeto excluam um comentário. Se o comentário tiver respostas, o fio é mantido e o comentário passa a ser exibido como removido.

#### Scenario: Comentário sem respostas
- **WHEN** o autor exclui um comentário sem respostas
- **THEN** o comentário deixa de aparecer na lista

#### Scenario: Comentário com respostas
- **WHEN** o dono exclui um comentário que tem duas respostas
- **THEN** o comentário aparece como removido, sem texto, e as duas respostas continuam visíveis

### Requirement: Resolver e reabrir
O sistema SHALL permitir que o autor do comentário, o dono e os editores marquem um comentário como resolvido e o reabram. Respostas MUST NOT ser marcadas isoladamente.

#### Scenario: Editor resolve
- **WHEN** um editor marca como resolvido o comentário de um ouvinte
- **THEN** o comentário aparece como resolvido, com quem resolveu e quando

#### Scenario: Ouvinte tenta resolver comentário alheio
- **WHEN** um ouvinte tenta resolver um comentário de outra pessoa
- **THEN** o sistema rejeita com erro de permissão
