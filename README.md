# Trackflow

Plataforma de colaboração para produtores musicais: cada projeto é uma faixa, cada versão é um bounce processado de forma assíncrona e comentado em pontos exatos da forma de onda.

> Em construção. A visão geral, o diagrama de arquitetura e as decisões principais entram ao fim do MVP (`openspec/changes/add-trackflow-mvp/`).

## Rodando localmente

Pré-requisitos: JDK 25, Docker e (para os testes do worker) ffmpeg.

```bash
cp .env.example .env                                  # valores locais; ajuste se quiser
docker compose --env-file .env -f docker/compose.yaml up -d
./mvnw -pl trackflow-api spring-boot:run -Dspring-boot.run.profiles=local
```

O `docker/compose.yaml` também funciona sem `.env`: os padrões são os mesmos valores do `.env.example`.

## Login de desenvolvimento

No profile `local` existe um login sem provedor externo, para usar a API sem criar apps OAuth. Fora desse profile a rota responde 404.

Toda requisição que altera estado precisa do token CSRF. O token chega no cookie `XSRF-TOKEN` e volta no cabeçalho `X-XSRF-TOKEN`:

```bash
# 1. Pega o cookie XSRF-TOKEN
curl -s -c cookies.txt http://localhost:8080/actuator/health > /dev/null
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' cookies.txt)

# 2. Entra como "produtor-a" (a conta de teste é criada no primeiro uso)
curl -s -b cookies.txt -c cookies.txt \
  -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"name": "produtor-a"}' \
  http://localhost:8080/api/dev/login

# 3. A sessão vale para as próximas chamadas
curl -s -b cookies.txt http://localhost:8080/api/me
```

O nome aceita letras minúsculas, dígitos e hífen (2 a 60 caracteres). O mesmo nome sempre entra na mesma conta.

## Login com GitHub

1. Em <https://github.com/settings/developers>, clique em **New OAuth App**.
2. Preencha:
   - **Application name**: `Trackflow (local)`
   - **Homepage URL**: `http://localhost:8080`
   - **Authorization callback URL**: `http://localhost:8080/login/oauth2/code/github`
3. Depois de criar, gere um **client secret**.
4. No `.env`, preencha `TRACKFLOW_GITHUB_CLIENT_ID` e `TRACKFLOW_GITHUB_CLIENT_SECRET`.
5. Exporte as variáveis antes de subir a API (o Spring não lê o `.env` sozinho):

   ```bash
   set -a; source .env; set +a
   ./mvnw -pl trackflow-api spring-boot:run -Dspring-boot.run.profiles=local
   ```

6. Abra <http://localhost:8080/oauth2/authorization/github>. Depois de autorizar, a conta é criada e a sessão começa.

## Login com Google

Mesmo fluxo, no [Google Cloud Console](https://console.cloud.google.com/apis/credentials): crie um **OAuth client ID** do tipo *Web application* com o redirect `http://localhost:8080/login/oauth2/code/google` e preencha `TRACKFLOW_GOOGLE_CLIENT_ID` e `TRACKFLOW_GOOGLE_CLIENT_SECRET`.

Contas de provedores diferentes nunca são unidas pelo e-mail: entrar pelo GitHub e pelo Google com o mesmo e-mail cria duas contas.

## Encerrar a sessão

```bash
curl -s -b cookies.txt -H "X-XSRF-TOKEN: $XSRF" -X POST http://localhost:8080/logout   # 204
```
