#!/usr/bin/env bash
# 校验"发出去的 Java 包用户真的拉得到"：轮询 repo1.maven.org 上各模块的 .pom 到 HTTP 200。
#
# 为什么要有这个脚本：release.yml 原先只有 publish 步，`mvn deploy` 退 0 就打绿勾，
# 而 Sonatype → repo1.maven.org 的同步有 ~15min 延迟（首次 404 是时序不是失败），
# 于是"CI 绿"不等于"用户拉得到"（issues 台账记过一次假绿）。
#
# 用法：scripts/verify-maven-central.sh <version>
#   可调环境变量：
#     MAVEN_BASE      默认 https://repo1.maven.org/maven2
#     VERIFY_TIMEOUT  总超时秒，默认 1500（25min，覆盖传播尾）
#     VERIFY_INTERVAL 轮询间隔秒，默认 30
#     VERIFY_MODULES  模块列表（空格分隔），默认下面 7 个（demo-boot4 不参与发布）
set -uo pipefail

BASE="${MAVEN_BASE:-https://repo1.maven.org/maven2}"
TIMEOUT="${VERIFY_TIMEOUT:-1500}"
INTERVAL="${VERIFY_INTERVAL:-30}"
GROUP_PATH="com/mldong/jeeflow"
DEFAULT_MODULES="jeeflow-core jeeflow-repository-jdbc jeeflow-persist \
jeeflow-spring-boot-autoconfigure jeeflow-spring-boot2-starter \
jeeflow-spring-boot3-starter jeeflow-spring-boot4-starter"
MODULES="${VERIFY_MODULES:-$DEFAULT_MODULES}"

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "用法: $0 <version>   （如 1.8.28）" >&2
  exit 2
fi

missing() { # 打印还没 200 的模块
  local m code
  for m in $MODULES; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 \
              "$BASE/$GROUP_PATH/$m/$VERSION/$m-$VERSION.pom" || echo 000)
    [ "$code" != "200" ] && echo "$m($code)"
  done
}

deadline=$(( $(date +%s) + TIMEOUT ))
echo "校验 $VERSION @ $BASE（超时 ${TIMEOUT}s / 间隔 ${INTERVAL}s，模块 $(echo $MODULES | wc -w) 个）"
while :; do
  pend=$(missing | tr '\n' ' ' | sed 's/ *$//')
  if [ -z "$pend" ]; then
    echo "✅ 全部模块的 .pom 已在 repo1.maven.org 可见（用户可拉取）"
    exit 0
  fi
  left=$(( deadline - $(date +%s) ))
  if [ "$left" -le 0 ]; then
    echo "❌ 超时仍有模块不可见: $pend" >&2
    exit 1
  fi
  echo "  待同步: $pend（剩余 ${left}s）"
  sleep "$INTERVAL"
done
