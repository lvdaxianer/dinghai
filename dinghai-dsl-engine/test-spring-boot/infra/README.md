# Real database integration environment

`docker-compose.yml` starts fixed-version local-only MySQL, PostgreSQL and Doris instances. The Doris all-in-one image contains the FE/BE test runtime and exposes the MySQL protocol port; the application still selects `mysql`, so it uses `MysqlDataAdapter`.

Run the bounded integration suite from the repository root:

```bash
cp dinghai-dsl-engine/test-spring-boot/.env.example /tmp/dinghai-dsl-engine-it.env
set -a; . /tmp/dinghai-dsl-engine-it.env; set +a
dinghai-dsl-engine/test-spring-boot/scripts/run-real-integration.sh
```

The script fails immediately with an actionable message when the Docker daemon or Docker Compose is unavailable. It tears down only this Compose project and its test volumes on exit. The default credentials are local test values, not production credentials; override them through environment variables and never commit real secrets.
