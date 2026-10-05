#!/bin/bash
# 내 PC에서 실행: GitHub Actions가 만든 jar를 받아 ORCL_2의 spring-server만 재배포한다.
# 사용: orcl2-deploy-from-actions.sh [RUN_ID]   (생략하면 main의 최근 성공 실행)
# 안전장치: 받은 jar의 커밋이 로컬 HEAD와 다르면 중단한다 (FORCE=1이면 무시)
# 롤백: ssh ORCL_2 'docker tag kotlin-spring-lotto-generator:prev-<TS> kotlin-spring-lotto-generator:latest && cd ~ && docker compose up -d --no-deps spring-server'
set -euo pipefail
REPO=seogineer/kotlin-spring-lotto-generator
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"

RUN_ID=${1:-$(gh run list -R "$REPO" --branch main --workflow ci.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId')}
[ -n "$RUN_ID" ] || { echo "ABORT: 성공한 Actions 실행이 없음"; exit 1; }
RUN_SHA=$(gh run view "$RUN_ID" -R "$REPO" --json headSha --jq .headSha)
HEAD_SHA=$(git rev-parse HEAD)
echo "run=$RUN_ID commit=${RUN_SHA:0:7} local_head=${HEAD_SHA:0:7}"
if [ "$RUN_SHA" != "$HEAD_SHA" ] && [ "${FORCE:-0}" != "1" ]; then
  echo "ABORT: 받을 jar의 커밋이 로컬 HEAD와 다름 (그래도 진행하려면 FORCE=1)"; exit 1
fi

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
gh run download "$RUN_ID" -R "$REPO" -n kotlin-spring-lotto-generator-jar -D "$TMP"
JAR="$TMP/kotlin-spring-lotto-generator.jar"
[ -f "$JAR" ] || { echo "ABORT: jar 아티팩트 없음"; exit 1; }
sha256sum "$JAR"
TS=$(date +%Y%m%d%H%M)

scp "$JAR" ORCL_2:/home/ubuntu/kotlin-spring-lotto-generator.jar.new

ssh ORCL_2 "TS=$TS bash -s" <<'REMOTE'
set -euo pipefail
cd ~
# 1) 롤백용 백업: 현재 이미지에 태그, 현재 jar 보관
docker tag kotlin-spring-lotto-generator:latest "kotlin-spring-lotto-generator:prev-$TS"
cp -p kotlin-spring-lotto-generator.jar "kotlin-spring-lotto-generator.jar.bak.$TS"
mv kotlin-spring-lotto-generator.jar.new kotlin-spring-lotto-generator.jar

# 2) 이미지 빌드 -> 교체 기동. 빌드 실패 시 기존 컨테이너를 내리지 않는다.
if ! docker compose build spring-server; then
  echo "ABORT: 이미지 빌드 실패. 기존 spring-server는 그대로 실행 중"
  mv "kotlin-spring-lotto-generator.jar.bak.$TS" kotlin-spring-lotto-generator.jar
  exit 1
fi
docker compose up -d --no-deps spring-server

# 3) 기동 확인 (최대 6분)
ok=0
for i in $(seq 1 72); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/drawings/frequent || true)
  [ "$code" = "200" ] && { echo "spring-server up after $((i*5))s"; ok=1; break; }
  sleep 5
done
if [ "$ok" != "1" ]; then
  echo "!! 기동 실패: 이전 이미지로 복구"
  docker tag "kotlin-spring-lotto-generator:prev-$TS" kotlin-spring-lotto-generator:latest
  docker compose up -d --no-deps spring-server
  exit 1
fi
echo "== startup log =="
docker logs spring-server 2>&1 | grep -E "Started |캐시 워밍업|ERROR|Exception" | cut -c1-220 | tail -10
docker ps --format '{{.Names}} {{.Status}}' | grep spring-server
free -m | sed -n 2,3p
REMOTE
