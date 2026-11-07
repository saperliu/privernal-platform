#!/usr/bin/env bash
#
# 动态表单 ⇄ 工作流 端到端回归脚本
#
# 覆盖场景（与「保存草稿 / 发起 / 重启 / 状态回写 / 取名 / 免登」需求一一对应）：
#   A. 自建一条测试流程 → 校验流程实例标题「发起人姓名的流程名」与待办任务标题继承
#   B. 动态表单：免登提交被拒 → 保存草稿(status=0) → 发起(status=2) → 重复发起被拦截
#                → 审批通过(status=1) → 重启得到新实例 → 终止(status=3)
#
# 前置条件：
#   1. 后端已启动（默认 http://localhost:8600），且已用最新代码重启（application.yaml 免登白名单、
#      status=0 草稿、findTableCodeByFlowCode 反查这三处改动必须已生效）
#   2. TABLE_CODE 指向的表单已启用工作流（dynamic_form.is_workflow=1 且 workflow_code 非空），
#      且该流程编码下存在「已发布」的流程定义
#   3. 机器上有 curl 与 jq
#   4. 测试账号需具备该表的按钮权限（dynamic:{TABLE_CODE}:list/add/edit/remove 或超管 *:*:*）：
#      /dbs/open/dynamic/** 在登录校验之外已按表鉴权，缺权限时返回体 code=500「没有权限，请联系管理员授权」
#      （默认 admin 满足）。本脚本不覆盖 data/relationList：它按「引用方表单」传递授权，
#      口径见 privernal-service-dynamic/README-FEATURES.md 第 6 节
#
# 用法：
#   ./TEST-DYNAMIC-FLOW.sh                                   # 用默认账号与 ai_result 表单
#   TABLE_CODE=my_form FORM_DATA='{"name":"回归用例"}' ./TEST-DYNAMIC-FLOW.sh
#   APPROVER_NAME=manager APPROVER_PASSWORD=manager123 ./TEST-DYNAMIC-FLOW.sh   # 审批人与发起人不同账号
#   RUN_DYNAMIC=0 ./TEST-DYNAMIC-FLOW.sh                     # 只跑流程取名回归
#   KEEP_DATA=1 ./TEST-DYNAMIC-FLOW.sh                       # 保留测试数据便于排查
#
# 退出码：0 全部通过；1 存在 FAIL；2 环境/参数不满足
#
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8600}"
LOGIN_NAME="${LOGIN_NAME:-admin}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-admin123}"
LOGIN_TYPE="${LOGIN_TYPE:-2}"
TABLE_CODE="${TABLE_CODE:-ai_result}"
FLOW_CODE="${FLOW_CODE:-}"                      # 不传则读表单的 workflow_code
TEST_FLOW_CODE="${TEST_FLOW_CODE:-flow_e2e_title_test}"
TEST_FLOW_NAME="${TEST_FLOW_NAME:-E2E取名回归流程}"
APPROVER_NAME="${APPROVER_NAME:-$LOGIN_NAME}"    # 测试流程的审批人就是发起人自己（script:INITIATOR）
APPROVER_PASSWORD="${APPROVER_PASSWORD:-$LOGIN_PASSWORD}"
RUN_DYNAMIC="${RUN_DYNAMIC:-1}"
RUN_FLOW="${RUN_FLOW:-1}"
KEEP_DATA="${KEEP_DATA:-0}"
FORM_DATA="${FORM_DATA:-}"                       # 新增数据的业务字段，JSON 对象；不传则从已有数据推导模板

TS="$(date +%m%d%H%M%S)"
TITLE_SUFFIX="的"
STATUS_DRAFT=0
STATUS_FINISHED=1
STATUS_RUNNING=2
STATUS_CANCELED=3

pass=0
fail=0
skip=0
TMP_BODY="$(mktemp)"
DATA_IDS=()

cleanup() { rm -f "$TMP_BODY" 2>/dev/null; }
trap cleanup EXIT

ok()  { printf '  \033[32mPASS\033[0m %s\n' "$1"; pass=$((pass + 1)); }
bad() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; fail=$((fail + 1)); }
skp() { printf '  \033[33mSKIP\033[0m %s\n' "$1"; skip=$((skip + 1)); }
title() { printf '\n\033[1m%s\033[0m\n' "$1"; }

need_tools() {
  for t in curl jq; do
    command -v "$t" >/dev/null 2>&1 || { echo "缺少依赖：$t" >&2; exit 2; }
  done
}

# req METHOD PATH [BODY] [TOKEN]  →  结果放入 RESP，HTTP 状态码放入 HTTP_CODE
RESP=""
HTTP_CODE=""
req() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-s -o "$TMP_BODY" -w '%{http_code}' -X "$method" "$BASE_URL$path"
              -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-d "$body")
  HTTP_CODE="$(curl "${args[@]}" 2>/dev/null)" || HTTP_CODE="000"
  RESP="$(cat "$TMP_BODY" 2>/dev/null)"
}

# jq -r，取不到时输出空串
jf() { printf '%s' "$RESP" | jq -r "$1 // empty" 2>/dev/null; }

# assert_eq 名称 期望 实际
assert_eq() {
  if [[ -n "$3" && "$2" == "$3" ]]; then ok "$1"
  else bad "$1（期望=$2 实际=${3:-<空>}）"; fi
}

# assert_code200 名称 —— 断言业务响应 code==200
assert_ok200() {
  local code; code="$(jf .code)"
  if [[ "$code" == "200" ]]; then ok "$1"
  else bad "$1（code=${code:-?} http=$HTTP_CODE msg=$(jf .message)）"; fi
}

# assert_rejected 名称 —— 断请求被拒绝（免登失效、幂等拦截等）
assert_rejected() {
  local code; code="$(jf .code)"
  if [[ "$HTTP_CODE" == "401" || "$HTTP_CODE" == "403" || ( -n "$code" && "$code" != "200" ) ]]; then
    ok "$1（code=${code:-?} http=$HTTP_CODE msg=$(jf .message)）"
  else
    bad "$1 期望被拒绝，但请求成功了"
  fi
}

assert_contains() {
  if [[ "$3" == *"$2"* ]]; then ok "$1"
  else bad "$1（期望包含=$2 实际=$3）"; fi
}

# login_to 账号 密码 → 结果写入 TOKEN_OUT（用函数返回值会开子 shell，丢掉 RESP，失败时打印不出服务端消息）
TOKEN_OUT=""
login_to() {
  req POST /auth/login "{\"loginName\":\"$1\",\"password\":\"$2\",\"loginType\":$LOGIN_TYPE}"
  TOKEN_OUT="$(printf '%s' "$RESP" | jq -r '.data.tokenValue // .data.token // empty')"
}

# ---------------------------------------------------------------- 登录
need_tools
title "环境检查"
echo "  BASE_URL   = $BASE_URL"
echo "  发起人     = $LOGIN_NAME"
echo "  审批人     = $APPROVER_NAME"
echo "  动态表单   = $TABLE_CODE"

login_to "$LOGIN_NAME" "$LOGIN_PASSWORD"
INITIATOR_TOKEN="$TOKEN_OUT"
if [[ -z "$INITIATOR_TOKEN" ]]; then
  echo "  登录失败：$(printf '%s' "$RESP" | head -c 300)" >&2
  echo "  请确认账号密码与 $BASE_URL 是否可访问（-H 'Authorization: Bearer <token>'，sa-token.token-prefix=Bearer）" >&2
  exit 2
fi
ok "发起人登录成功"

APPROVER_TOKEN="$INITIATOR_TOKEN"
if [[ "$APPROVER_NAME" != "$LOGIN_NAME" ]]; then
  login_to "$APPROVER_NAME" "$APPROVER_PASSWORD"
  APPROVER_TOKEN="$TOKEN_OUT"
  [[ -n "$APPROVER_TOKEN" ]] && ok "审批人登录成功" || echo "  审批人登录失败，涉及审批的步骤将 SKIP" >&2
fi

req GET /back/system/user/getInfo "" "$INITIATOR_TOKEN"
NICK_NAME="$(jf '.data.nickName')"
[[ -n "$NICK_NAME" ]] && ok "取到发起人姓名：$NICK_NAME" || bad "无法从 /back/system/user/getInfo 取到 nickName，取名断言将失败"

# ---------------------------------------------------------------- A. 取名 / 取人回归
if [[ "$RUN_FLOW" == "1" ]]; then
  title "A. 测试流程与「发起人姓名的流程名」取名回归"

  req GET "/back/flow/definition/code/$TEST_FLOW_CODE" "" "$INITIATOR_TOKEN"
  PUBLISHED_ID="$(printf '%s' "$RESP" | jq -r --arg c "$TEST_FLOW_CODE" \
    '[.data[]? | select(.flowCode==$c and .isPublish==1)][-1].id // empty' 2>/dev/null)"
  PUBLISHED_NAME="$(printf '%s' "$RESP" | jq -r --arg c "$TEST_FLOW_CODE" \
    '[.data[]? | select(.flowCode==$c and .isPublish==1)][-1].flowName // empty' 2>/dev/null)"

  if [[ -n "$PUBLISHED_ID" ]]; then
    ok "测试流程 $TEST_FLOW_CODE 已有发布版（id=$PUBLISHED_ID），复用不重复建版"
  else
    req POST /back/flow/definition/save "$(cat <<EOF
{
  "flowCode": "$TEST_FLOW_CODE",
  "flowName": "$TEST_FLOW_NAME",
  "formCustom": "N",
  "nodeList": [
    {"nodeCode": "start", "nodeType": 1, "nodeName": "开始", "coordinate": "{\"x\":120,\"y\":160}"},
    {"nodeCode": "task_e2e", "nodeType": 2, "nodeName": "回归审批", "coordinate": "{\"x\":360,\"y\":160}",
     "permissionFlag": "script:INITIATOR"},
    {"nodeCode": "end", "nodeType": 4, "nodeName": "结束", "coordinate": "{\"x\":600,\"y\":160}"}
  ],
  "skipList": [
    {"nowNodeCode": "start", "nextNodeCode": "task_e2e", "skipType": "PASS"},
    {"nowNodeCode": "task_e2e", "nextNodeCode": "end", "skipType": "PASS"}
  ]
}
EOF
    )" "$INITIATOR_TOKEN"
    DRAFT_ID="$(jf '.data.id')"
    [[ -n "$DRAFT_ID" ]] && ok "测试流程定义已创建（id=$DRAFT_ID）" || bad "创建测试流程定义失败：$(jf .message)"

    if [[ -n "$DRAFT_ID" ]]; then
      req PUT "/back/flow/definition/publish/$DRAFT_ID" "" "$INITIATOR_TOKEN"
      assert_ok200 "测试流程定义已发布"
    fi
    req GET "/back/flow/definition/code/$TEST_FLOW_CODE" "" "$INITIATOR_TOKEN"
    PUBLISHED_ID="$(printf '%s' "$RESP" | jq -r --arg c "$TEST_FLOW_CODE" \
      '[.data[]? | select(.flowCode==$c and .isPublish==1)][-1].id // empty' 2>/dev/null)"
    PUBLISHED_NAME="$(printf '%s' "$RESP" | jq -r --arg c "$TEST_FLOW_CODE" \
      '[.data[]? | select(.flowCode==$c and .isPublish==1)][-1].flowName // empty' 2>/dev/null)"
  fi

  if [[ -z "$PUBLISHED_ID" ]]; then
    skp "测试流程无可用发布版，A 段后续步骤跳过"
  else
    BUSINESS_ID="E2E-TITLE-$TS"
    req POST /back/flow/instance/save \
      "{\"flowCode\":\"$TEST_FLOW_CODE\",\"businessId\":\"$BUSINESS_ID\",\"variables\":{\"e2e\":\"$TS\"}}" \
      "$INITIATOR_TOKEN"
    INSTANCE_ID="$(jf '.data.instanceId')"
    assert_ok200 "发起测试流程实例"

    if [[ -z "$INSTANCE_ID" ]]; then
      skp "未取得 instanceId，A 段后续步骤跳过"
    else
      req GET "/back/flow/instance/byBusiness/$BUSINESS_ID" "" "$INITIATOR_TOKEN"
      assert_eq "实例处于运行中" "true" "$(jf '.data.running')"

      # 取名：实例标题 = 发起人姓名 + 的 + 流程名
      req POST /back/flow/instance/my/list "{\"pageNum\":1,\"pageSize\":5,\"businessId\":\"$BUSINESS_ID\"}" "$INITIATOR_TOKEN"
      INSTANCE_TITLE="$(printf '%s' "$RESP" | jq -r --arg i "$INSTANCE_ID" \
        '[.data.list[]? | select((.id|tostring)==$i)][0].flowName // empty' 2>/dev/null)"
      EXPECT_TITLE="$NICK_NAME$TITLE_SUFFIX$PUBLISHED_NAME"
      assert_eq "流程实例标题按「发起人姓名的流程名」生成" "$EXPECT_TITLE" "$INSTANCE_TITLE"

      # 取人：script:INITIATOR 解析出发起人，任务应出现在发起人待办里，且标题继承实例标题
      req POST /back/flow/task/todo/list '{"pageNum":1,"pageSize":50}' "$APPROVER_TOKEN"
      TASK_ID="$(printf '%s' "$RESP" | jq -r --arg i "$INSTANCE_ID" \
        '[.data.list[]? | select((.instanceId|tostring)==$i)][0].id // empty' 2>/dev/null)"
      TASK_TITLE="$(printf '%s' "$RESP" | jq -r --arg i "$INSTANCE_ID" \
        '[.data.list[]? | select((.instanceId|tostring)==$i)][0].flowName // empty' 2>/dev/null)"
      if [[ -z "$TASK_ID" ]]; then
        bad "取人失败：发起人待办里没有该实例的任务（permissionFlag=script:INITIATOR 未解析出办理人）"
      else
        ok "取人成功：任务 $TASK_ID 出现在发起人待办"
        assert_eq "待办任务标题继承实例标题" "$EXPECT_TITLE" "$TASK_TITLE"
        req PUT /back/flow/task/approve "{\"taskId\":$TASK_ID,\"message\":\"E2E 通过\"}" "$APPROVER_TOKEN"
        assert_ok200 "审批通过"
        req GET "/back/flow/instance/byBusiness/$BUSINESS_ID" "" "$INITIATOR_TOKEN"
        assert_eq "审批后流程已结束（不再运行）" "false" "$(jf '.data.running')"
      fi
    fi
  fi
fi

# ---------------------------------------------------------------- B. 动态表单全链路
if [[ "$RUN_DYNAMIC" == "1" ]]; then
  title "B. 动态表单：免登 → 保存草稿 → 发起 → 审批 → 重启 → 终止"

  # B0 免登白名单（需求项 7）：空 token 在登录校验就被拒（拦截器在按表鉴权之前，此处不涉权限码）
  req POST /dbs/open/dynamic/data/add "{\"tableCode\":\"$TABLE_CODE\",\"startFlow\":false}" ""
  assert_rejected "未登录提交动态数据被拒绝（add 已移出免登白名单）"

  # B1 表单绑定校验
  DYN_ABORT=0
  req GET "/back/dynamic/form/code/$TABLE_CODE" "" "$INITIATOR_TOKEN"
  FORM_IS_WORKFLOW="$(jf '.data.isWorkflow')"
  FORM_FLOW_CODE="$(jf '.data.workflowCode')"
  if [[ "$FORM_IS_WORKFLOW" != "1" ]]; then
    bad "表单 $TABLE_CODE 未启用工作流（isWorkflow=${FORM_IS_WORKFLOW:-<空>}），B 段需要流程表单"
    DYN_ABORT=1
  else
    ok "表单 $TABLE_CODE 已启用工作流，绑定流程 $FORM_FLOW_CODE"
    [[ -z "$FLOW_CODE" ]] && FLOW_CODE="$FORM_FLOW_CODE"
    req GET "/back/flow/definition/code/$FLOW_CODE" "" "$INITIATOR_TOKEN"
    DEF_NAME="$(printf '%s' "$RESP" | jq -r --arg c "$FLOW_CODE" \
      '[.data[]? | select(.flowCode==$c and .isPublish==1)][-1].flowName // empty' 2>/dev/null)"
    if [[ -z "$DEF_NAME" ]]; then
      bad "流程 $FLOW_CODE 没有已发布版本，无法发起（请先在设计器发布）"
      DYN_ABORT=1
    else
      ok "流程 $FLOW_CODE 已发布，名称：$DEF_NAME"
    fi
  fi

  # B2 业务数据 payload：显式 FORM_DATA 优先，否则用已有第一条数据的业务字段做模板
  if [[ -z "$FORM_DATA" && "$DYN_ABORT" != "1" ]]; then
    req POST /dbs/open/dynamic/data/list "{\"tableCode\":\"$TABLE_CODE\",\"pageNum\":1,\"pageSize\":1}" "$INITIATOR_TOKEN"
    FORM_DATA="$(printf '%s' "$RESP" | jq -c '
      def syskeys: ["id","status","deleted_flag","deletedFlag","create_by","createBy","create_time","createTime",
                    "created_at","createdAt","update_by","updateBy","update_time","updateTime","updated_at","updatedAt",
                    "organization_id","organizationId","department_id","departmentId","app_id","appId",
                    "table_name","tableName","meta_data","metaData","is_workflow","isWorkflow",
                    "workflow_code","workflowCode","key_field_map","keyFieldMap","remark","total"];
      (.data.list[0] // {})
      | with_entries(select(.key as $k | (syskeys | index($k)) | not))
      | with_entries(select(.value != null))
      | with_entries(select((.key | test("_at$|_by$|_time$")) | not))
    ' 2>/dev/null)"
    [[ "$FORM_DATA" == "null" || -z "$FORM_DATA" ]] && FORM_DATA="{}"
    # 第一个文本字段打上标记，便于在业务列表里认出这条测试数据
    FORM_DATA="$(printf '%s' "$FORM_DATA" | jq -c --arg ts "E2E-$TS" '
      if length > 0 then (keys_unsorted[0] as $k | if (($k | type)=="string" and (.[$k] | type)=="string")
                        then .[$k] = $ts else . end) else . end' 2>/dev/null)"
    [[ -z "$FORM_DATA" ]] && FORM_DATA="{}"
    echo "  自动推导的业务字段：$FORM_DATA"
  fi

  # B3 保存草稿（需求项 4、6）
  DRAFT_PAYLOAD="$(printf '%s' "$FORM_DATA" | jq -c '. + {tableCode:$t, startFlow:false}' --arg t "$TABLE_CODE" 2>/dev/null)"
  if [[ "$DYN_ABORT" == "1" ]]; then
    DATA_ID=""
  else
    req POST /dbs/open/dynamic/data/add "$DRAFT_PAYLOAD" "$INITIATOR_TOKEN"
    DATA_ID="$(jf '.data.id')"
    assert_ok200 "保存草稿请求成功"
  fi
  if [[ -z "$DATA_ID" ]]; then
    skp "未取得数据 id，B3 之后的步骤跳过"
  else
    DATA_IDS+=("$DATA_ID")
    req GET "/dbs/open/dynamic/$TABLE_CODE/$DATA_ID" "" "$INITIATOR_TOKEN"
    assert_eq "草稿数据业务状态为 0（草稿）" "$STATUS_DRAFT" "$(jf '.data.status')"

    # B4 显式发起（需求项 5 的「发起」入口）
    req POST /dbs/open/dynamic/data/startFlow "{\"tableCode\":\"$TABLE_CODE\",\"id\":\"$DATA_ID\"}" "$INITIATOR_TOKEN"
    DYN_INSTANCE_ID="$(jf '.data.instanceId')"
    assert_ok200 "草稿发起流程成功"
    if [[ -n "$DYN_INSTANCE_ID" ]]; then
      req GET "/dbs/open/dynamic/$TABLE_CODE/$DATA_ID" "" "$INITIATOR_TOKEN"
      assert_eq "发起后业务状态回写为 2（流程中）" "$STATUS_RUNNING" "$(jf '.data.status')"

      req POST /back/flow/instance/my/list "{\"pageNum\":1,\"pageSize\":5,\"businessId\":\"$DATA_ID\"}" "$INITIATOR_TOKEN"
      DYN_TITLE="$(printf '%s' "$RESP" | jq -r --arg i "$DYN_INSTANCE_ID" \
        '[.data.list[]? | select((.id|tostring)==$i)][0].flowName // empty' 2>/dev/null)"
      assert_eq "表单发起的实例标题为「发起人姓名的流程名」" "$NICK_NAME$TITLE_SUFFIX$DEF_NAME" "$DYN_TITLE"

      # B5 幂等：运行中不允许重复发起
      req POST /dbs/open/dynamic/data/startFlow "{\"tableCode\":\"$TABLE_CODE\",\"id\":\"$DATA_ID\"}" "$INITIATOR_TOKEN"
      assert_rejected "运行中的数据重复发起被拦截"
      assert_contains "重复发起的拦截文案可识别" "进行中" "$(jf .message)"
    else
      skp "发起未返回实例（可能表单未绑定流程或取人失败），B4~B7 跳过"
    fi

    # B6 审批通过 → 状态回写 1（已完成）
    FIRST_INSTANCE="$DYN_INSTANCE_ID"
    if [[ -n "$DYN_INSTANCE_ID" ]]; then
      req POST /back/flow/task/todo/list '{"pageNum":1,"pageSize":50}' "$APPROVER_TOKEN"
      DYN_TASK_ID="$(printf '%s' "$RESP" | jq -r --arg i "$DYN_INSTANCE_ID" \
        '[.data.list[]? | select((.instanceId|tostring)==$i)][0].id // empty' 2>/dev/null)"
      if [[ -z "$DYN_TASK_ID" ]]; then
        skp "当前审批人待办中找不到该实例任务（换个审批人账号重跑：APPROVER_NAME=xxx APPROVER_PASSWORD=xxx），跳过审批步骤"
      else
        req PUT /back/flow/task/approve "{\"taskId\":$DYN_TASK_ID,\"message\":\"E2E 通过\"}" "$APPROVER_TOKEN"
        assert_ok200 "表单流程审批通过"
        req GET "/dbs/open/dynamic/$TABLE_CODE/$DATA_ID" "" "$INITIATOR_TOKEN"
        assert_eq "审批结束后业务状态为 1（已完成）" "$STATUS_FINISHED" "$(jf '.data.status')"
      fi
    fi

    # B7 终止 → 状态 3，再「重启」新建第二条实例（需求项 5）
    if [[ -n "$FIRST_INSTANCE" ]]; then
      req GET "/back/flow/instance/byBusiness/$DATA_ID" "" "$INITIATOR_TOKEN"
      if [[ "$(jf '.data.running')" == "true" ]]; then
        req PUT "/back/flow/instance/terminate/$FIRST_INSTANCE?message=E2E%E7%BB%88%E6%AD%A2" "" "$INITIATOR_TOKEN"
        assert_ok200 "终止第一条实例"
        req GET "/dbs/open/dynamic/$TABLE_CODE/$DATA_ID" "" "$INITIATOR_TOKEN"
        assert_eq "终止后业务状态为 3（已取消）" "$STATUS_CANCELED" "$(jf '.data.status')"
      fi
      req POST /dbs/open/dynamic/data/startFlow "{\"tableCode\":\"$TABLE_CODE\",\"id\":\"$DATA_ID\"}" "$INITIATOR_TOKEN"
      SECOND_INSTANCE_ID="$(jf '.data.instanceId')"
      assert_ok200 "已结束的数据可以重启流程"
      if [[ -n "$SECOND_INSTANCE_ID" && "$SECOND_INSTANCE_ID" != "$FIRST_INSTANCE" ]]; then
        ok "重启创建了新的流程实例（$FIRST_INSTANCE → $SECOND_INSTANCE_ID）"
        req GET "/dbs/open/dynamic/$TABLE_CODE/$DATA_ID" "" "$INITIATOR_TOKEN"
        assert_eq "重启后业务状态回到 2（流程中）" "$STATUS_RUNNING" "$(jf '.data.status')"
        req PUT "/back/flow/instance/terminate/$SECOND_INSTANCE_ID?message=E2E%E6%B8%85%E7%90%86" "" "$INITIATOR_TOKEN"
        assert_ok200 "清理：终止重启出的实例"
      else
        bad "重启未产生新实例（instanceId=${SECOND_INSTANCE_ID:-<空>}）"
      fi
    fi
  fi
fi

# ---------------------------------------------------------------- 清理
if [[ "${#DATA_IDS[@]}" -gt 0 && "$KEEP_DATA" != "1" ]]; then
  title "清理测试数据"
  for id in "${DATA_IDS[@]}"; do
    req DELETE "/dbs/open/dynamic/$TABLE_CODE/$id" "" "$INITIATOR_TOKEN"
    [[ "$(jf .code)" == "200" ]] && ok "已删除测试数据 $id" || bad "删除测试数据 $id 失败：$(jf .message)"
  done
elif [[ "${#DATA_IDS[@]}" -gt 0 ]]; then
  echo -e "\n  保留了测试数据：${DATA_IDS[*]}（表 $TABLE_CODE）"
fi

title "结果"
printf '  PASS=%d  FAIL=%d  SKIP=%d\n' "$pass" "$fail" "$skip"
if [[ "$fail" -gt 0 ]]; then
  echo "  存在失败项，请结合后端日志定位（流程状态回写失败会打 error 日志但不阻断主链路）"
  exit 1
fi
echo "  全部通过"
exit 0
