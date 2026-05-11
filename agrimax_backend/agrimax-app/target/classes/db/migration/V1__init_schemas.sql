-- =============================================
-- AgriMax V1 Migration: Create all module schemas and tables
-- =============================================

-- ── SCHEMAS ──
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS marketplace;
CREATE SCHEMA IF NOT EXISTS fintech;
CREATE SCHEMA IF NOT EXISTS notification;

-- ══════════════════════════════════════════════
-- IDENTITY MODULE
-- ══════════════════════════════════════════════

CREATE TABLE identity.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(15) NOT NULL UNIQUE,
    name            VARCHAR(100),
    role            VARCHAR(20) NOT NULL DEFAULT 'FARMER',
    language_pref   VARCHAR(5) DEFAULT 'ur',
    is_kyc_verified BOOLEAN DEFAULT FALSE,
    profile_image   TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE identity.otp_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES identity.users(id) ON DELETE CASCADE,
    otp_code    VARCHAR(6) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    verified    BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE identity.kyc_documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES identity.users(id) ON DELETE CASCADE,
    doc_type    VARCHAR(30) NOT NULL,
    doc_url     TEXT NOT NULL,
    status      VARCHAR(20) DEFAULT 'PENDING',
    reviewed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ══════════════════════════════════════════════
-- CATALOG MODULE
-- ══════════════════════════════════════════════

CREATE TABLE catalog.crops (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_en     VARCHAR(100) NOT NULL,
    name_ur     VARCHAR(100) NOT NULL,
    category    VARCHAR(50),
    unit        VARCHAR(20) DEFAULT 'kg',
    image_url   TEXT,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE catalog.crop_prices (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    crop_id     UUID REFERENCES catalog.crops(id) ON DELETE CASCADE,
    market_name VARCHAR(100),
    min_price   DECIMAL(12,2),
    max_price   DECIMAL(12,2),
    avg_price   DECIMAL(12,2),
    source      VARCHAR(50) DEFAULT 'MANUAL',
    price_date  DATE NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE catalog.ai_insights (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    crop_id         UUID REFERENCES catalog.crops(id) ON DELETE CASCADE,
    insight_type    VARCHAR(30) NOT NULL,
    content_en      TEXT,
    content_ur      TEXT,
    confidence      DECIMAL(3,2),
    valid_until     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ══════════════════════════════════════════════
-- MARKETPLACE MODULE
-- ══════════════════════════════════════════════

CREATE TABLE marketplace.sell_posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID NOT NULL,
    crop_id         UUID NOT NULL,
    quantity        DECIMAL(10,2) NOT NULL,
    unit            VARCHAR(20) DEFAULT 'kg',
    asking_price    DECIMAL(12,2) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    location        VARCHAR(200),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE marketplace.bids (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID REFERENCES marketplace.sell_posts(id) ON DELETE CASCADE,
    buyer_id    UUID NOT NULL,
    bid_price   DECIMAL(12,2) NOT NULL,
    message     TEXT,
    status      VARCHAR(20) DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE marketplace.deals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID REFERENCES marketplace.sell_posts(id),
    bid_id          UUID REFERENCES marketplace.bids(id),
    seller_id       UUID NOT NULL,
    buyer_id        UUID NOT NULL,
    final_price     DECIMAL(12,2) NOT NULL,
    quantity        DECIMAL(10,2) NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ══════════════════════════════════════════════
-- FINTECH MODULE
-- ══════════════════════════════════════════════

CREATE TABLE fintech.wallets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE,
    balance     DECIMAL(14,2) DEFAULT 0.00,
    currency    VARCHAR(3) DEFAULT 'PKR',
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE fintech.transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id       UUID REFERENCES fintech.wallets(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL,
    amount          DECIMAL(14,2) NOT NULL,
    reference_id    UUID,
    reference_type  VARCHAR(30),
    description     TEXT,
    status          VARCHAR(20) DEFAULT 'COMPLETED',
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE fintech.escrow (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id     UUID NOT NULL UNIQUE,
    buyer_id    UUID NOT NULL,
    seller_id   UUID NOT NULL,
    amount      DECIMAL(14,2) NOT NULL,
    status      VARCHAR(20) DEFAULT 'HELD',
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    released_at TIMESTAMPTZ
);

-- ══════════════════════════════════════════════
-- NOTIFICATION MODULE
-- ══════════════════════════════════════════════

CREATE TABLE notification.notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    type        VARCHAR(20) NOT NULL,
    title_en    VARCHAR(200),
    title_ur    VARCHAR(200),
    body_en     TEXT,
    body_ur     TEXT,
    is_read     BOOLEAN DEFAULT FALSE,
    metadata    JSONB,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ── INDEXES ──
CREATE INDEX idx_users_phone ON identity.users(phone_number);
CREATE INDEX idx_otp_user ON identity.otp_tokens(user_id);
CREATE INDEX idx_crop_prices_crop_date ON catalog.crop_prices(crop_id, price_date);
CREATE INDEX idx_sell_posts_seller ON marketplace.sell_posts(seller_id);
CREATE INDEX idx_sell_posts_status ON marketplace.sell_posts(status);
CREATE INDEX idx_bids_post ON marketplace.bids(post_id);
CREATE INDEX idx_deals_seller ON marketplace.deals(seller_id);
CREATE INDEX idx_deals_buyer ON marketplace.deals(buyer_id);
CREATE INDEX idx_wallet_user ON fintech.wallets(user_id);
CREATE INDEX idx_transactions_wallet ON fintech.transactions(wallet_id);
CREATE INDEX idx_notifications_user ON notification.notifications(user_id, is_read);
