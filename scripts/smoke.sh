#!/usr/bin/env bash
# Teste de fumaça ponta a ponta (design D15): sobe a infraestrutura, roda API e worker, faz login
# de desenvolvimento, envia um WAV gerado na hora direto ao storage, espera a versão ficar pronta e
# comenta nela. Termina com código 0 só se tudo isso funcionar.
#
# Requisitos: Docker, JDK 25 (JAVA_HOME), ffmpeg, curl e jq.
# Uso: ./scripts/smoke.sh
set -euo pipefail

cd "$(dirname "$0")/.."

API="http://localhost:8080"
WORK="target/smoke"
COOKIES="$WORK/cookies.txt"
READY_TIMEOUT_SECONDS=180
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

# Mesmos valores locais do .env.example; o worker não tem padrão para as chaves do storage.
export TRACKFLOW_STORAGE_ENDPOINT="${TRACKFLOW_STORAGE_ENDPOINT:-http://localhost:8333}"
export TRACKFLOW_STORAGE_ACCESS_KEY="${TRACKFLOW_STORAGE_ACCESS_KEY:-trackflow}"
export TRACKFLOW_STORAGE_SECRET_KEY="${TRACKFLOW_STORAGE_SECRET_KEY:-trackflow}"

# No Windows (Git Bash) o jq termina as linhas com CRLF; o \r sobraria em URLs e cabeçalhos.
jq() { command jq "$@" | tr -d '\r'; }

step() { printf '\n==> %s\n' "$*"; }
fail() { printf '\nSMOKE FAILED: %s\n' "$*" >&2; exit 1; }

for tool in docker curl jq ffmpeg "$JAVA"; do
    command -v "$tool" > /dev/null || fail "'$tool' não encontrado no PATH"
done

PIDS=()
cleanup() {
    for pid in "${PIDS[@]}"; do
        kill "$pid" 2> /dev/null || true
    done
}
trap cleanup EXIT

rm -rf "$WORK"
mkdir -p "$WORK"

step "Subindo PostgreSQL, RabbitMQ e SeaweedFS"
docker compose -f docker/compose.yaml up -d --wait postgres rabbitmq seaweedfs
docker compose -f docker/compose.yaml up --exit-code-from seaweedfs-init seaweedfs-init > /dev/null

step "Empacotando API e worker"
./mvnw -B -q package -DskipTests

step "Iniciando API (profile local) e worker"
"$JAVA" -jar trackflow-api/target/trackflow-api-*.jar --spring.profiles.active=local > "$WORK/api.log" 2>&1 &
PIDS+=($!)
"$JAVA" -jar trackflow-worker/target/trackflow-worker-*.jar > "$WORK/worker.log" 2>&1 &
PIDS+=($!)

for _ in $(seq 1 60); do
    curl -sf "$API/actuator/health" > /dev/null && break
    sleep 2
done
curl -sf "$API/actuator/health" > /dev/null || fail "a API não respondeu; veja $WORK/api.log"

# Requisições que alteram estado levam o token CSRF que o servidor deixa no cookie XSRF-TOKEN.
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIES"; }
call() {
    local method=$1 path=$2 body=${3:-}
    curl -sS -f -b "$COOKIES" -c "$COOKIES" -X "$method" \
        -H "X-XSRF-TOKEN: $(xsrf)" -H "Content-Type: application/json" \
        ${body:+-d "$body"} "$API$path"
}

step "Login de desenvolvimento"
curl -s -c "$COOKIES" "$API/actuator/health" > /dev/null
call POST /api/dev/login '{"name": "smoke-test"}' > /dev/null
[ "$(call GET /api/me | jq -r .displayName)" = "smoke-test" ] || fail "login não abriu a sessão"

step "Criando projeto"
PROJECT=$(call POST /api/projects '{"title": "Smoke Test", "genre": "tech house"}' | jq -r .id)

step "Gerando um WAV de 20 s e pedindo o envio"
WAV="$WORK/bounce.wav"
ffmpeg -nostdin -v error -y -f lavfi -i "sine=frequency=1000:beep_factor=4:duration=20" "$WAV"
SIZE=$(wc -c < "$WAV" | tr -d ' ')
TICKET=$(call POST "/api/projects/$PROJECT/versions/uploads" \
    "{\"fileName\": \"bounce.wav\", \"contentType\": \"audio/wav\", \"sizeBytes\": $SIZE}")
VERSION=$(jq -r .version.id <<< "$TICKET")

step "Enviando direto ao storage pela URL assinada"
HEADERS=()
while IFS= read -r header; do
    HEADERS+=(-H "$header")
done < <(jq -r '.upload.headers | to_entries[] | select(.key | ascii_downcase != "content-length") | "\(.key): \(.value)"' <<< "$TICKET")
# "Expect:" desliga o 100-continue que o curl manda acima de 1 MB; o SeaweedFS recusa esse PUT.
curl -sS -f --max-time 60 -X PUT "${HEADERS[@]}" -H "Expect:" --data-binary "@$WAV" "$(jq -r .upload.url <<< "$TICKET")" > /dev/null

step "Confirmando a versão"
NUMBER=$(call POST "/api/projects/$PROJECT/versions/$VERSION/confirm" | jq -r .number)
[ "$NUMBER" = "1" ] || fail "a primeira versão deveria ser a 1, veio $NUMBER"

step "Esperando o processamento (até ${READY_TIMEOUT_SECONDS} s)"
STATUS=""
for _ in $(seq 1 $((READY_TIMEOUT_SECONDS / 2))); do
    STATUS=$(call GET "/api/projects/$PROJECT/versions/$VERSION" | jq -r .status)
    [ "$STATUS" = "ready" ] || [ "$STATUS" = "failed" ] && break
    sleep 2
done
[ "$STATUS" = "ready" ] || fail "versão terminou como '$STATUS'; veja $WORK/worker.log"
call GET "/api/projects/$PROJECT/versions/$VERSION" | jq -c .metrics

step "Comentando no segundo 5"
COMMENT=$(call POST "/api/projects/$PROJECT/versions/$VERSION/comments" \
    '{"positionMs": 5000, "body": "kick embolando com o baixo"}' | jq -r .author.name)
[ "$COMMENT" = "smoke-test" ] || fail "o comentário não voltou com o autor"

step "Tudo certo"
