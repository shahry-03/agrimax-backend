#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# AgriMax Backend — Master API Test Suite (v3)
# ═══════════════════════════════════════════════════════════════

BASE="http://localhost:8080"
PASS=0; FAIL=0; SKIP=0
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

# ── Helpers ──
step()  { echo -e "\n${CYAN}${BOLD}▶ $1${NC}"; }
ok()    { echo -e "${GREEN}✅ $1${NC}"; PASS=$((PASS+1)); }
fail()  { echo -e "${RED}❌ $1${NC}"; FAIL=$((FAIL+1)); }
skip()  { echo -e "${YELLOW}⏭  $1${NC}"; SKIP=$((SKIP+1)); }
info()  { echo -e "   $1"; }

assert() {
  if [[ "$2" == "$3" ]]; then ok "$1"; else
    fail "$1"
    info "  Expected: $3"
    info "  Actual:   $2"
  fi
}

acontains() {
  if echo "$2" | grep -qE "$3"; then ok "$1"; else
    fail "$1"
    info "  Looking for: $3"
    info "  In: ${2:0:120}..."
  fi
}

jqget() { echo "$1" | jq -r "$2" 2>/dev/null; }
jqnum() { echo "$1" | jq -r "$2" 2>/dev/null | sed 's/\.00$//'; }  # 🆕 Decimal fix

# ═══════════════════════════════════════════════════════════════
# PREFLIGHT
# ═══════════════════════════════════════════════════════════════
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  AgriMax Backend — Master Test Suite (v3)${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"

step "Preflight checks"
command -v jq >/dev/null || { fail "jq not installed"; exit 1; }
ok "jq installed"

if curl -sf "$BASE/api/v1/ai/tip" >/dev/null 2>&1; then
  ok "Backend is up at $BASE"
else
  fail "Backend not reachable at $BASE — start it first"
  exit 1
fi

PHONE_FARMER="03001111111"
EMAIL_BUYER="buyer_$(date +%s)@test.com"
PASSWORD="Test@1234"
ADMIN_EMAIL="admin@agrimax.local"
ADMIN_PASSWORD="Admin@1234"

# ═══════════════════════════════════════════════════════════════
# 1. FARMER AUTH (OTP)
# ═══════════════════════════════════════════════════════════════
step "1. Farmer login via OTP"

curl -s -X POST "$BASE/api/v1/auth/send-otp" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$PHONE_FARMER\"}" >/dev/null
info "OTP sent to $PHONE_FARMER — copy from backend logs"

read -p "$(echo -e ${YELLOW}"   Enter OTP from backend log: "${NC})" FARMER_OTP

FRESP=$(curl -s -X POST "$BASE/api/v1/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$PHONE_FARMER\",\"otp\":\"$FARMER_OTP\"}")
FARMER_TOKEN=$(jqget "$FRESP" '.data.accessToken')
assert "Farmer OTP login" "$(jqget "$FRESP" '.success')" "true"
[[ -n "$FARMER_TOKEN" && "$FARMER_TOKEN" != "null" ]] && ok "Farmer token received" || fail "No farmer token"
FARMER_ROLE=$(jqget "$FRESP" '.data.role')
assert "Farmer role" "$FARMER_ROLE" "FARMER"

# ═══════════════════════════════════════════════════════════════
# 2. BUYER REGISTER + AUTO EMAIL VERIFY (DEV)
# ═══════════════════════════════════════════════════════════════
step "2. Buyer registration + email verification"

BRE=$(curl -s -X POST "$BASE/api/v1/auth/buyer/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_BUYER\",\"password\":\"$PASSWORD\",\"name\":\"Test Buyer\"}")
assert "Buyer register" "$(jqget "$BRE" '.success')" "true"
assert "Buyer email not yet verified" "$(jqget "$BRE" '.data.emailVerified')" "false"

L1=$(curl -s -X POST "$BASE/api/v1/auth/buyer/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_BUYER\",\"password\":\"$PASSWORD\"}")
acontains "Login blocked until email verified" "$(jqget "$L1" '.error')" "EMAIL_NOT_VERIFIED"

echo -e "${YELLOW}   Calling DEV endpoint to verify email & trigger Wallet creation...${NC}"
VER=$(curl -s -X POST "$BASE/api/v1/dev/users/verify-email?email=$EMAIL_BUYER")
assert "Auto email verify (DEV)" "$(jqget "$VER" '.success')" "true"
info "Wallet auto-created via UserRegisteredEvent ✅"

# ═══════════════════════════════════════════════════════════════
# 3. BUYER LOGIN
# ═══════════════════════════════════════════════════════════════
step "3. Buyer login (email + password)"

BL=$(curl -s -X POST "$BASE/api/v1/auth/buyer/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_BUYER\",\"password\":\"$PASSWORD\"}")
assert "Buyer login success" "$(jqget "$BL" '.success')" "true"
BUYER_TOKEN=$(jqget "$BL" '.data.accessToken')
assert "Buyer role" "$(jqget "$BL" '.data.role')" "BUYER"
assert "Buyer preferred mode" "$(jqget "$BL" '.data.preferredMode')" "TEXT"
[[ -n "$BUYER_TOKEN" ]] && ok "Buyer token received"

# ═══════════════════════════════════════════════════════════════
# 4. ADMIN LOGIN
# ═══════════════════════════════════════════════════════════════
step "4. Admin login"

AL=$(curl -s -X POST "$BASE/api/v1/auth/buyer/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
assert "Admin login success" "$(jqget "$AL" '.success')" "true"
ADMIN_TOKEN=$(jqget "$AL" '.data.accessToken')
assert "Admin role" "$(jqget "$AL" '.data.role')" "ADMIN"

# ═══════════════════════════════════════════════════════════════
# 5. WALLET TOP-UP (BUYER)
# ═══════════════════════════════════════════════════════════════
step "5. Buyer wallet top-up (dev)"

TOP=$(curl -s -X POST "$BASE/api/v1/dev/wallet/topup" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d '{"amount": 500000}')
assert "Top-up 500,000 PKR" "$(jqget "$TOP" '.success')" "true"

BW=$(curl -s -H "Authorization: Bearer $BUYER_TOKEN" "$BASE/api/v1/wallet/balance")
assert "Buyer wallet balance" "$(jqnum "$BW" '.data.balance')" "500000"

# ═══════════════════════════════════════════════════════════════
# 6. FARMER CREATES POST (voice)
# ═══════════════════════════════════════════════════════════════
step "6. Farmer creates sell post via VOICE"

POSTRESP=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{"transcript":"mujhe 100 kilo gehu 3000 mein bechna hai","sessionId":"test-s1"}')
assert "Intent = CREATE_SELL_POST" "$(jqget "$POSTRESP" '.data.intent')" "CREATE_SELL_POST"
assert "Post created" "$(jqget "$POSTRESP" '.success')" "true"
assert "UI action navigate" "$(jqget "$POSTRESP" '.data.uiAction.type')" "NAVIGATE"
POST_ID=$(jqget "$POSTRESP" '.data.result.postId')
[[ -n "$POST_ID" && "$POST_ID" != "null" ]] && ok "Post ID: $POST_ID" || fail "No postId"

# ═══════════════════════════════════════════════════════════════
# 7. BUYER PLACES BID (REST)
# ═══════════════════════════════════════════════════════════════
step "7. Buyer places bid via REST"

BIDRESP=$(curl -s -X POST "$BASE/api/v1/marketplace/posts/$POST_ID/bids" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d '{"bidPrice": 2900, "message": "Achhi quality ho to le lunga"}')
assert "Bid placed" "$(jqget "$BIDRESP" '.success')" "true"
BID_ID=$(jqget "$BIDRESP" '.data.id')
[[ -n "$BID_ID" && "$BID_ID" != "null" ]] && ok "Bid ID: $BID_ID" || fail "No bidId"

# ═══════════════════════════════════════════════════════════════
# 8. FARMER ACCEPTS BID (voice) → ESCROW HOLD
# ═══════════════════════════════════════════════════════════════
step "8. Farmer accepts bid via VOICE → escrow holds funds"

ACCEPT=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d "{\"transcript\":\"is bid ko accept karo\",\"sessionId\":\"test-s1\",\"contextBidId\":\"$BID_ID\"}")
assert "Intent = ACCEPT_BID" "$(jqget "$ACCEPT" '.data.intent')" "ACCEPT_BID"
assert "Deal created" "$(jqget "$ACCEPT" '.success')" "true"
DEAL_ID=$(jqget "$ACCEPT" '.data.result.dealId')
[[ -n "$DEAL_ID" && "$DEAL_ID" != "null" ]] && ok "Deal ID: $DEAL_ID" || fail "No dealId"

# Verify escrow: buyer wallet should be 500,000 - 2900 = 497100
BW2=$(curl -s -H "Authorization: Bearer $BUYER_TOKEN" "$BASE/api/v1/wallet/balance")
assert "Buyer wallet after escrow hold" "$(jqnum "$BW2" '.data.balance')" "497100"

# ═══════════════════════════════════════════════════════════════
# 9. BUYER COMPLETES DEAL (REST) → ESCROW RELEASE
# ═══════════════════════════════════════════════════════════════
step "9. Buyer completes deal → escrow releases to farmer"

COMP=$(curl -s -X POST "$BASE/api/v1/marketplace/deals/$DEAL_ID/complete" \
  -H "Authorization: Bearer $BUYER_TOKEN")
assert "Deal completed" "$(jqget "$COMP" '.success')" "true"
assert "Deal status COMPLETED" "$(jqget "$COMP" '.data.status')" "COMPLETED"

# Verify farmer wallet = 2900
FW=$(curl -s -H "Authorization: Bearer $FARMER_TOKEN" "$BASE/api/v1/wallet/balance")
assert "Farmer wallet after payout" "$(jqnum "$FW" '.data.balance')" "2900"

# ═══════════════════════════════════════════════════════════════
# 10. CANCEL FLOW (fresh post + bid + cancel)
# ═══════════════════════════════════════════════════════════════
step "10. Cancel flow — refund back to buyer"

P2=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{"transcript":"50 kilo chawal 2000 mein bechna hai","sessionId":"test-s1"}')
POST2_ID=$(jqget "$P2" '.data.result.postId')
[[ -n "$POST2_ID" && "$POST2_ID" != "null" ]] && ok "Post2: $POST2_ID" || fail "No post2"

B2=$(curl -s -X POST "$BASE/api/v1/marketplace/posts/$POST2_ID/bids" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d '{"bidPrice": 1900}')
BID2_ID=$(jqget "$B2" '.data.id')
[[ -n "$BID2_ID" && "$BID2_ID" != "null" ]] && ok "Bid2: $BID2_ID" || fail "No bid2"

A2=$(curl -s -X POST "$BASE/api/v1/marketplace/bids/$BID2_ID/accept" \
  -H "Authorization: Bearer $FARMER_TOKEN")
DEAL2_ID=$(jqget "$A2" '.data.id')
[[ -n "$DEAL2_ID" && "$DEAL2_ID" != "null" ]] && ok "Deal2: $DEAL2_ID" || fail "No deal2"

# Cancel → buyer refunded. Balance was 497100 - 1900 = 495200. After refund = 497100
CAN=$(curl -s -X POST "$BASE/api/v1/marketplace/deals/$DEAL2_ID/cancel" \
  -H "Authorization: Bearer $BUYER_TOKEN")
assert "Deal cancelled" "$(jqget "$CAN" '.success')" "true"
assert "Deal status CANCELLED" "$(jqget "$CAN" '.data.status')" "CANCELLED"

BW3=$(curl -s -H "Authorization: Bearer $BUYER_TOKEN" "$BASE/api/v1/wallet/balance")
assert "Buyer wallet after refund" "$(jqnum "$BW3" '.data.balance')" "497100"

# ═══════════════════════════════════════════════════════════════
# 11. VOICE MISC — price, navigate, help, stop
# ═══════════════════════════════════════════════════════════════
step "11. Voice misc intents"

VP=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -d '{"transcript":"gehu ki price kya hai","sessionId":"anon-v"}')
assert "Anonymous price intent" "$(jqget "$VP" '.data.intent')" "GET_CROP_PRICE"

VN=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{"transcript":"market kholo","sessionId":"test-s1"}')
assert "Navigate intent" "$(jqget "$VN" '.data.intent')" "NAVIGATE"

VH=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -d '{"transcript":"madad chahiye","sessionId":"anon-v"}')
assert "Help intent" "$(jqget "$VH" '.data.intent')" "HELP"

VS=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{"transcript":"bas shukriya","sessionId":"test-s1"}')
assert "Stop listening intent" "$(jqget "$VS" '.data.intent')" "STOP_LISTENING"
assert "Stop action" "$(jqget "$VS" '.data.uiAction.type')" "STOP_LISTENING"

# ═══════════════════════════════════════════════════════════════
# 12. BUYER-ONLY INTENT
# ═══════════════════════════════════════════════════════════════
step "12. Buyer-only intent (VIEW_MY_BIDS)"

VB=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d '{"transcript":"meri bids dikhao","sessionId":"test-s2"}')
assert "Buyer bids intent" "$(jqget "$VB" '.data.intent')" "VIEW_MY_BIDS"

VFB=$(curl -s -X POST "$BASE/api/v1/voice/command" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{"transcript":"meri bids dikhao","sessionId":"test-s1"}')
assert "Farmer blocked from VIEW_MY_BIDS" "$(jqget "$VFB" '.success')" "false"

# ═══════════════════════════════════════════════════════════════
# 13. DASHBOARDS (all 3 roles)
# ═══════════════════════════════════════════════════════════════
step "13. Role dashboards"

FD=$(curl -s -H "Authorization: Bearer $FARMER_TOKEN" "$BASE/api/v1/farmer/dashboard")
assert "Farmer dashboard" "$(jqget "$FD" '.success')" "true"
assert "Farmer has earnings" "$(jqnum "$FD" '.data.totalEarnings')" "2900"

BD=$(curl -s -H "Authorization: Bearer $BUYER_TOKEN" "$BASE/api/v1/buyer/dashboard")
assert "Buyer dashboard" "$(jqget "$BD" '.success')" "true"
assert "Buyer has totalSpent" "$(jqnum "$BD" '.data.totalSpent')" "2900"

AD=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/dashboard")
assert "Admin dashboard" "$(jqget "$AD" '.success')" "true"
acontains "Admin sees farmers count" "$(jqget "$AD" '.data.totalFarmers')" "^[0-9]+$"

# ═══════════════════════════════════════════════════════════════
# 14. NOTIFICATIONS
# ═══════════════════════════════════════════════════════════════
step "14. Notifications"

FN=$(curl -s -H "Authorization: Bearer $FARMER_TOKEN" "$BASE/api/v1/notifications")
assert "Farmer notifications" "$(jqget "$FN" '.success')" "true"
FC=$(echo "$FN" | jq '.data | length')
info "Farmer notification count: $FC"

BN=$(curl -s -H "Authorization: Bearer $BUYER_TOKEN" "$BASE/api/v1/notifications")
BC=$(echo "$BN" | jq '.data | length')
info "Buyer notification count: $BC"

U=$(curl -s -H "Authorization: Bearer $BUYER_TOKEN" "$BASE/api/v1/notifications/unread-count")
assert "Unread count endpoint" "$(jqget "$U" '.success')" "true"

# ═══════════════════════════════════════════════════════════════
# 15. SESSIONS
# ═══════════════════════════════════════════════════════════════
step "15. Session management"

for i in 1 2; do
  curl -s -X POST "$BASE/api/v1/auth/buyer/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL_BUYER\",\"password\":\"$PASSWORD\"}" >/dev/null
done

SESS=$(curl -s -H "Authorization: Bearer $BUYER_TOKEN" "$BASE/api/v1/users/me/sessions")
assert "List sessions" "$(jqget "$SESS" '.success')" "true"
SC=$(echo "$SESS" | jq '.data | length')
info "Active sessions: $SC"
[[ "$SC" -ge 2 ]] && ok "Multiple sessions tracked" || fail "Expected ≥2 sessions"

REV=$(curl -s -X DELETE -H "Authorization: Bearer $BUYER_TOKEN" \
  "$BASE/api/v1/users/me/sessions")
assert "Revoke other sessions" "$(jqget "$REV" '.success')" "true"

# ═══════════════════════════════════════════════════════════════
# 16. 2FA (if oathtool available)
# ═══════════════════════════════════════════════════════════════
step "16. Two-factor authentication"

if ! command -v oathtool &>/dev/null; then
  skip "oathtool not installed — 2FA verify test skipped"
  info "Install: sudo apt install oathtool"
else
  S2=$(curl -s -X POST -H "Authorization: Bearer $BUYER_TOKEN" \
    "$BASE/api/v1/users/me/2fa/setup")
  assert "2FA setup" "$(jqget "$S2" '.success')" "true"
  TOTP_SECRET=$(jqget "$S2" '.data.secret')
  info "TOTP secret: $TOTP_SECRET"

  sleep 2
  TOTP_CODE=$(oathtool --totp -b "$TOTP_SECRET")
  EN=$(curl -s -X POST "$BASE/api/v1/users/me/2fa/enable" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $BUYER_TOKEN" \
    -d "{\"code\":\"$TOTP_CODE\"}")
  assert "2FA enable" "$(jqget "$EN" '.success')" "true"
  BACKUP_CODES=$(jqget "$EN" '.data.backupCodes | length')
  info "Backup codes received: $BACKUP_CODES"

  L2=$(curl -s -X POST "$BASE/api/v1/auth/buyer/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL_BUYER\",\"password\":\"$PASSWORD\"}")
  assert "Login requires 2FA" "$(jqget "$L2" '.data.requiresTwoFactor')" "true"
  TEMP_TOKEN=$(jqget "$L2" '.data.tempToken')

  sleep 2
  TOTP_CODE2=$(oathtool --totp -b "$TOTP_SECRET")
  V2=$(curl -s -X POST "$BASE/api/v1/auth/buyer/2fa/verify" \
    -H "Content-Type: application/json" \
    -d "{\"tempToken\":\"$TEMP_TOKEN\",\"code\":\"$TOTP_CODE2\"}")
  assert "2FA verify + login" "$(jqget "$V2" '.success')" "true"

  sleep 2
  TOTP_CODE3=$(oathtool --totp -b "$TOTP_SECRET")
  DIS=$(curl -s -X POST "$BASE/api/v1/users/me/2fa/disable" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $BUYER_TOKEN" \
    -d "{\"password\":\"$PASSWORD\",\"code\":\"$TOTP_CODE3\"}")
  assert "2FA disable (cleanup)" "$(jqget "$DIS" '.success')" "true"
fi

# ═══════════════════════════════════════════════════════════════
# 17. ERROR CASES
# ═══════════════════════════════════════════════════════════════
step "17. Error cases / auth guards"

NOAUTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/wallet/balance")
assert "No token → 401" "$NOAUTH" "401"

FBID=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{"bidPrice":1000}' \
  "$BASE/api/v1/marketplace/posts/$POST_ID/bids")
assert "Farmer bidding → 403" "$FBID" "403"

BFD=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  "$BASE/api/v1/farmer/dashboard")
assert "Buyer on farmer dashboard → 403" "$BFD" "403"

FA=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  "$BASE/api/v1/admin/dashboard")
assert "Farmer on admin dashboard → 403" "$FA" "403"

BAD=$(curl -s -X POST "$BASE/api/v1/auth/buyer/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_BUYER\",\"password\":\"WrongPass@1\"}")
assert "Wrong password rejected" "$(jqget "$BAD" '.success')" "false"

# ═══════════════════════════════════════════════════════════════
# 18. RATE LIMITING
# ═══════════════════════════════════════════════════════════════
step "18. Rate limiting (login — capacity 5, refill 15min)"

info "Sending 6 login attempts..."
RL_HIT=""
for i in $(seq 1 6); do
  R=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/buyer/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"ratelimit@test.com","password":"Xxx@1234"}')
  info "  Attempt $i → HTTP $R"
  [[ "$R" == "429" ]] && RL_HIT="yes"
done
[[ "$RL_HIT" == "yes" ]] && ok "Rate limit triggered (429)" || fail "Rate limit NOT triggered"

# ═══════════════════════════════════════════════════════════════
# SUMMARY
# ═══════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Test Summary${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}✅ PASSED: $PASS${NC}"
echo -e "  ${RED}❌ FAILED: $FAIL${NC}"
echo -e "  ${YELLOW}⏭  SKIPPED: $SKIP${NC}"
echo ""

TOTAL=$((PASS + FAIL))
if [[ $FAIL -eq 0 ]]; then
  echo -e "${GREEN}${BOLD}  🎉 ALL TESTS PASSED — backend is fully production-ready!${NC}"
  exit 0
else
  echo -e "${RED}${BOLD}  ⚠️  $FAIL test(s) failed — check above${NC}"
  exit 1
fi