#!/usr/bin/env bash
set -euo pipefail

# Simple Bash test runner for the Realtime Chat APIs.
# Requirements:
#   - curl
#   - jq

if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required but not installed. Install it (e.g. 'brew install jq' or 'sudo apt install jq')."
  exit 1
fi

read -rp "Base URL [default: http://localhost:8080]: " BASE_URL
BASE_URL=${BASE_URL:-http://localhost:8080}

echo "Using BASE_URL=${BASE_URL}"
echo

read -rp "User 1 displayName [default: User 1]: " USER1_NAME
USER1_NAME=${USER1_NAME:-User 1}

read -rp "User 2 displayName [default: User 2]: " USER2_NAME
USER2_NAME=${USER2_NAME:-User 2}

# Helper to pretty print section headers
section() {
  echo
  echo "============================================================"
  echo "$1"
  echo "============================================================"
}

# Helper to curl with nice formatting
call_api() {
  local METHOD=$1
  local URL=$2
  local HEADERS=$3
  local BODY=$4

  echo
  echo ">>> ${METHOD} ${URL}"
  if [[ -n "${HEADERS}" ]]; then
    echo ">>> Headers:"
    echo "${HEADERS}" | sed 's/^/    /'
  fi
  if [[ -n "${BODY}" ]]; then
    echo ">>> Request body:"
    echo "${BODY}" | jq '.' || echo "${BODY}"
  fi

  # Build curl command
  local CURL_CMD=(curl -sS -w "\nHTTP %{http_code}\n" -X "${METHOD}" "${URL}")

  # Append headers
  while read -r line; do
    [[ -z "${line}" ]] && continue
    CURL_CMD+=(-H "${line}")
  done <<< "${HEADERS}"

  # Append body if present
  if [[ -n "${BODY}" ]]; then
    CURL_CMD+=(-H "Content-Type: application/json" -d "${BODY}")
  fi

  echo
  echo ">>> Response:"
  "${CURL_CMD[@]}"
  echo
}

# 1) Create User 1
section "1) Creating User 1"
USER1_BODY=$(cat <<EOF
{
  "displayName": "${USER1_NAME}"
}
EOF
)

RESP_USER1=$(curl -sS -X POST "${BASE_URL}/users" \
  -H "Content-Type: application/json" \
  -d "${USER1_BODY}")

echo "Response:"
echo "${RESP_USER1}" | jq '.' || echo "${RESP_USER1}"
USER1_ID=$(echo "${RESP_USER1}" | jq -r '.id')
echo "User 1 ID: ${USER1_ID}"

# 2) Create User 2
section "2) Creating User 2"
USER2_BODY=$(cat <<EOF
{
  "displayName": "${USER2_NAME}"
}
EOF
)

RESP_USER2=$(curl -sS -X POST "${BASE_URL}/users" \
  -H "Content-Type: application/json" \
  -d "${USER2_BODY}")

echo "Response:"
echo "${RESP_USER2}" | jq '.' || echo "${RESP_USER2}"
USER2_ID=$(echo "${RESP_USER2}" | jq -r '.id')
echo "User 2 ID: ${USER2_ID}"

# 3) Create Chat (User1 + User2)
section "3) Creating chat between User 1 and User 2"

CHAT_BODY=$(cat <<EOF
{
  "participantIds": [${USER2_ID}],
  "group": false,
  "title": null
}
EOF
)

RESP_CHAT=$(curl -sS -X POST "${BASE_URL}/chats" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: ${USER1_ID}" \
  -d "${CHAT_BODY}")

echo "Response:"
echo "${RESP_CHAT}" | jq '.' || echo "${RESP_CHAT}"
CHAT_ID=$(echo "${RESP_CHAT}" | jq -r '.id')
echo "Chat ID: ${CHAT_ID}"

# 4) Send a message from User 1
section "4) Sending a message from User 1 via HTTP"

CLIENT_MSG_ID="http-test-1"
SEND_BODY=$(cat <<EOF
{
  "chatId": ${CHAT_ID},
  "body": "Hello from HTTP test script",
  "clientMessageId": "${CLIENT_MSG_ID}"
}
EOF
)

RESP_SEND=$(curl -sS -X POST "${BASE_URL}/messages/send" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: ${USER1_ID}" \
  -d "${SEND_BODY}")

echo "Response:"
echo "${RESP_SEND}" | jq '.' || echo "${RESP_SEND}"
MESSAGE_ID=$(echo "${RESP_SEND}" | jq -r '.id')
echo "Message ID: ${MESSAGE_ID}"

# 5) Get last 50 messages in chat as User1
section "5) Fetching last 50 messages for chat as User 1"

RESP_MESSAGES=$(curl -sS -X GET "${BASE_URL}/messages?chatId=${CHAT_ID}&limit=50" \
  -H "X-User-Id: ${USER1_ID}")

echo "Response:"
echo "${RESP_MESSAGES}" | jq '.' || echo "${RESP_MESSAGES}"

# 6) Mark that message as read by User 2
section "6) Marking message as read by User 2"

READ_BODY=$(cat <<EOF
{
  "chatId": ${CHAT_ID},
  "messageIds": [${MESSAGE_ID}]
}
EOF
)

RESP_READ=$(curl -sS -X POST "${BASE_URL}/messages/read" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: ${USER2_ID}" \
  -d "${READ_BODY}")

echo "Response:"
echo "${RESP_READ}" | jq '.' || echo "${RESP_READ}"

# 7) Get chat summary for both users
section "7) Fetching chat summaries"

echo "User 1 summaries:"
RESP_SUM_U1=$(curl -sS -X GET "${BASE_URL}/chats/summary" \
  -H "X-User-Id: ${USER1_ID}")
echo "${RESP_SUM_U1}" | jq '.' || echo "${RESP_SUM_U1}"

echo
echo "User 2 summaries:"
RESP_SUM_U2=$(curl -sS -X GET "${BASE_URL}/chats/summary" \
  -H "X-User-Id: ${USER2_ID}")
echo "${RESP_SUM_U2}" | jq '.' || echo "${RESP_SUM_U2}"

# 8) Get unread messages for User 2 (should be 0 after read)
section "8) Fetching unread messages for User 2 in chat"

RESP_UNREAD=$(curl -sS -X GET "${BASE_URL}/chats/${CHAT_ID}/messages/unread?limit=50" \
  -H "X-User-Id: ${USER2_ID}")

echo "Response:"
echo "${RESP_UNREAD}" | jq '.' || echo "${RESP_UNREAD}"

echo
echo "✅ API test flow completed."
echo "Users: ${USER1_ID} (${USER1_NAME}), ${USER2_ID} (${USER2_NAME})"
echo "Chat: ${CHAT_ID}, Last Message: ${MESSAGE_ID}"
