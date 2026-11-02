# ═══════════════════════════════════════════════════════
# SETUP: Two users (seller + buyer)
# ═══════════════════════════════════════════════════════

# 1. Seller signup
curl -X POST http://localhost:8080/api/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"03001111111"}'
# Copy OTP from logs

curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"03001111111","otp":"XXXXXX"}'
SELLER_TOKEN="..."

# 2. Buyer signup
curl -X POST http://localhost:8080/api/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"03002222222"}'
curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"03002222222","otp":"XXXXXX"}'
BUYER_TOKEN="..."

# ═══════════════════════════════════════════════════════
# TEST 1: Buyer needs money → dev top-up
# ═══════════════════════════════════════════════════════

curl -X POST http://localhost:8080/api/v1/dev/wallet/topup \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d '{"amount": 100000}'
# Buyer balance = 100,000 PKR

# ═══════════════════════════════════════════════════════
# TEST 2: Seller creates post
# ═══════════════════════════════════════════════════════

curl -X POST http://localhost:8080/api/v1/voice/command \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d '{"transcript":"mujhe 100 kilo gehu 3000 mein bechna hai","sessionId":"s1"}'
# → postId copy karo from response

POST_ID="..."

# ═══════════════════════════════════════════════════════
# TEST 3: Buyer places bid
# ═══════════════════════════════════════════════════════

curl -X POST http://localhost:8080/api/v1/marketplace/posts/$POST_ID/bids \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d '{"bidPrice": 2900, "message": "Achhi quality ho to le lunga"}'
BID_ID="..."

# ═══════════════════════════════════════════════════════
# TEST 4: Seller accepts bid → escrow holds buyer's money
# ═══════════════════════════════════════════════════════

curl -X POST http://localhost:8080/api/v1/marketplace/bids/$BID_ID/accept \
  -H "Authorization: Bearer $SELLER_TOKEN"
# → dealId in response
DEAL_ID="..."

# ─── Verify ───
# Buyer wallet: 100,000 → 100,000 - (2900 × 100) = 100,000 - 290,000 = ??? 
# Wait — 2900 * 100 = 290,000. But balance was only 100,000 → insuff balance!
# Fix: top-up 500,000 not 100,000

# ─── Retry with proper balance ───
curl -X POST http://localhost:8080/api/v1/dev/wallet/topup \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d '{"amount": 500000}'

# Then accept bid (via REST or voice)
curl -X POST http://localhost:8080/api/v1/marketplace/bids/$BID_ID/accept \
  -H "Authorization: Bearer $SELLER_TOKEN"

# Check: buyer wallet decreased, escrow HELD
curl -H "Authorization: Bearer $BUYER_TOKEN" \
  http://localhost:8080/api/v1/wallet/balance

# ═══════════════════════════════════════════════════════
# TEST 5: Buyer confirms delivery → escrow releases to seller
# ═══════════════════════════════════════════════════════

# Via REST
curl -X POST http://localhost:8080/api/v1/marketplace/deals/$DEAL_ID/complete \
  -H "Authorization: Bearer $BUYER_TOKEN"

# OR via voice
curl -X POST http://localhost:8080/api/v1/voice/command \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -d "{\"transcript\":\"delivery ho gayi paise release karo\",\"sessionId\":\"s2\",\"contextDealId\":\"$DEAL_ID\"}"

# ═══════════════════════════════════════════════════════
# TEST 6: Verify money flow
# ═══════════════════════════════════════════════════════

# Seller's wallet should have +290,000 now (minus 0)
curl -H "Authorization: Bearer $SELLER_TOKEN" \
  http://localhost:8080/api/v1/wallet/balance

# Check buyer notifications
curl -H "Authorization: Bearer $BUYER_TOKEN" \
  http://localhost:8080/api/v1/notifications

# Check seller notifications
curl -H "Authorization: Bearer $SELLER_TOKEN" \
  http://localhost:8080/api/v1/notifications

# ═══════════════════════════════════════════════════════
# TEST 7: Cancel flow (different deal)
# ═══════════════════════════════════════════════════════

# Seller create new post, buyer bid, seller accept → new deal
# Then cancel:
curl -X POST http://localhost:8080/api/v1/marketplace/deals/$DEAL2_ID/cancel \
  -H "Authorization: Bearer $BUYER_TOKEN"

# Verify:
# - Buyer wallet refunded
# - Post status = ACTIVE (re-listed)
# - Bid status = REJECTED