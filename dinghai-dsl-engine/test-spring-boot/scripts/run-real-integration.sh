#!/usr/bin/env bash
set -Eeuo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
module_dir="$project_root/dinghai-dsl-engine/test-spring-boot"
compose_file="$module_dir/infra/docker-compose.yml"
request_file="$(mktemp)"
response_file="$(mktemp)"
app_pid=""
compose_command=()

cleanup() {
    if [[ -n "$app_pid" ]]; then
        kill "$app_pid" 2>/dev/null || true
        wait "$app_pid" 2>/dev/null || true
    fi
    rm -f "$request_file" "$response_file"
    if ((${#compose_command[@]} > 0)); then
        "${compose_command[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon unavailable; start Docker/Colima before real integration tests." >&2
    exit 2
fi
if docker compose version >/dev/null 2>&1; then
    compose_command=(docker compose -f "$compose_file")
elif docker-compose version >/dev/null 2>&1; then
    compose_command=(docker-compose -f "$compose_file")
else
    echo "Docker Compose unavailable; install docker compose or docker-compose before real integration tests." >&2
    exit 2
fi

"${compose_command[@]}" up -d --wait mysql postgres doris
"${compose_command[@]}" run --rm doris-init

mvn -q -pl dinghai-dsl-engine/test-spring-boot -am -DskipTests install

cat > "$request_file" <<'JSON'
{"dslType":"relational-query","version":1,"payload":{"from":{"table":"fact_wireless_outage_timeline","alias":"f"},"select":[{"field":"f.event_date"},{"field":"a.asset_name"}],"joins":[{"type":"left","table":{"table":"outage_asset","alias":"a"},"on":[{"left":"f.asset_id","right":"a.asset_id"}]}],"where":[{"field":"f.status","operator":"eq","parameter":"status","logicalType":"string"}],"groupBy":[],"orderBy":[],"limit":10},"parameters":{"status":"OPEN"}}
JSON

start_test_app() {
    local adapter="$1"
    local jdbc_url="$2"
    local port="$3"
    local username="$4"
    local password="$5"
    local jdbc_driver="com.mysql.cj.jdbc.Driver"
    if [[ "$adapter" == "postgresql" ]]; then
        jdbc_driver="org.postgresql.Driver"
    fi
    DINGHAI_TEST_DATA_ADAPTER="$adapter" \
    DINGHAI_TEST_JDBC_URL="$jdbc_url" \
    DINGHAI_TEST_JDBC_DRIVER="$jdbc_driver" \
    DINGHAI_TEST_DB_USERNAME="$username" \
    DINGHAI_TEST_DB_PASSWORD="$password" \
    SERVER_PORT="$port" \
        mvn -q -pl dinghai-dsl-engine/test-spring-boot spring-boot:run >"$module_dir/target/real-http-$adapter.log" 2>&1 &
    app_pid=$!
    for attempt in $(seq 1 90); do
        if curl -fsS -o "$response_file" -H 'Content-Type: application/json' \
                --data-binary "@$request_file" "http://127.0.0.1:$port/api/test/dsl/validate"; then
            return
        fi
        sleep 1
    done
    echo "Spring Boot test app did not become ready for adapter=$adapter" >&2
    exit 1
}

assert_http_success() {
    local adapter="$1"
    local port="$2"
    curl --fail-with-body -sS -o "$response_file" -H 'Content-Type: application/json' \
        -H "X-Trace-Id: real-$adapter" --data-binary "@$request_file" \
        "http://127.0.0.1:$port/api/test/dsl/execute"
    if ! grep -Eq '"code"[[:space:]]*:[[:space:]]*"OK"' "$response_file"; then
        echo "HTTP DSL execution failed for adapter=$adapter" >&2
        sed -n '1,4p' "$response_file" >&2
        exit 1
    fi
    if ! grep -q '"asset-101"' "$response_file" || ! grep -q '"2026-11-13"' "$response_file"; then
        echo "HTTP DSL result values are incomplete for adapter=$adapter" >&2
        sed -n '1,8p' "$response_file" >&2
        exit 1
    fi
    if grep -q 'inactive-asset\|other-domain-asset\|2026-11-11' "$response_file"; then
        echo "HTTP DSL scope or logical-delete extension leaked rows for adapter=$adapter" >&2
        sed -n '1,8p' "$response_file" >&2
        exit 1
    fi
}

run_http_case() {
    local adapter="$1"
    local jdbc_url="$2"
    local port="$3"
    local username="$4"
    local password="$5"
    start_test_app "$adapter" "$jdbc_url" "$port" "$username" "$password"
    assert_http_success "$adapter" "$port"
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
    app_pid=""
}

run_http_case mysql \
    "jdbc:mysql://127.0.0.1:${DINGHAI_IT_MYSQL_PORT:-13306}/dinghai_test" \
    18080 dinghai_test "${DINGHAI_IT_DB_PASSWORD:-dinghai_local_only}"
run_http_case postgresql \
    "jdbc:postgresql://127.0.0.1:${DINGHAI_IT_POSTGRES_PORT:-15432}/dinghai_test" \
    18081 dinghai_test "${DINGHAI_IT_DB_PASSWORD:-dinghai_local_only}"
run_http_case mysql \
    "jdbc:mysql://127.0.0.1:${DINGHAI_IT_DORIS_PORT:-19030}/dinghai_test" \
    18082 root "${DINGHAI_IT_DORIS_PASSWORD:-}"

DINGHAI_IT_MYSQL_URL="jdbc:mysql://127.0.0.1:${DINGHAI_IT_MYSQL_PORT:-13306}/dinghai_test" \
DINGHAI_IT_POSTGRES_URL="jdbc:postgresql://127.0.0.1:${DINGHAI_IT_POSTGRES_PORT:-15432}/dinghai_test" \
DINGHAI_IT_DORIS_URL="jdbc:mysql://127.0.0.1:${DINGHAI_IT_DORIS_PORT:-19030}/dinghai_test" \
DINGHAI_IT_DB_PASSWORD="${DINGHAI_IT_DB_PASSWORD:-dinghai_local_only}" \
    mvn -q -pl dinghai-dsl-engine/test-spring-boot -am \
        -Ddinghai.integration.enabled=true \
        -Dtest=RealMysqlIntegrationTest,RealPostgresqlIntegrationTest,RealDorisIntegrationTest \
        -Dsurefire.failIfNoSpecifiedTests=false test

echo "Real MySQL, Doris(MySQL protocol), PostgreSQL JDBC and HTTP integration tests passed."
