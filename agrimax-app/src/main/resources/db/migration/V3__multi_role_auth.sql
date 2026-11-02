-- ============================================================
-- V3: Multi-role auth + reference-code infrastructure
-- ============================================================

-- ── Extend identity.users ──
ALTER TABLE identity.users
    ALTER COLUMN phone_number DROP NOT NULL,
    ADD COLUMN email             VARCHAR(300) UNIQUE,
    ADD COLUMN password          VARCHAR(100),
    ADD COLUMN email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_secret       VARCHAR(255),
    ADD COLUMN two_factor_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_attempts INT      NOT NULL DEFAULT 0,
    ADD COLUMN locked_until      TIMESTAMPTZ,
    ADD COLUMN last_failed_login TIMESTAMPTZ,
    ADD COLUMN enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN preferred_mode    VARCHAR(10)  DEFAULT 'VOICE',
    ADD COLUMN provider          VARCHAR(20)  DEFAULT 'LOCAL',
    ADD COLUMN provider_id       VARCHAR(100);

-- At least one identifier must exist
ALTER TABLE identity.users
    ADD CONSTRAINT users_phone_or_email_chk
    CHECK (phone_number IS NOT NULL OR email IS NOT NULL);

-- Backfill preferred_mode based on role
UPDATE identity.users SET preferred_mode = 'VOICE' WHERE role = 'FARMER';
UPDATE identity.users SET preferred_mode = 'TEXT'  WHERE role IN ('BUYER','ADMIN');

-- ── RBAC tables ──
CREATE TABLE identity.roles (
    role_id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name   VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE identity.permissions (
    permission_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_name VARCHAR(100) UNIQUE NOT NULL,
    description     VARCHAR(500)
);

CREATE TABLE identity.user_roles (
    user_id UUID REFERENCES identity.users(id)      ON DELETE CASCADE,
    role_id UUID REFERENCES identity.roles(role_id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE identity.role_permissions (
    role_id       UUID REFERENCES identity.roles(role_id)             ON DELETE CASCADE,
    permission_id UUID REFERENCES identity.permissions(permission_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ── Refresh tokens (reference-style rotation) ──
CREATE TABLE identity.refresh_tokens (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    jti                VARCHAR(255) UNIQUE NOT NULL,
    user_id            UUID         NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    revoked            BOOLEAN      NOT NULL DEFAULT FALSE,
    replaced_by_token  VARCHAR(255),
    ip_address         VARCHAR(45),
    user_agent         VARCHAR(500),
    last_used_at       TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_jti     ON identity.refresh_tokens(jti);
CREATE INDEX idx_refresh_tokens_user_id ON identity.refresh_tokens(user_id);

-- ── Verification tokens (email verify + password reset) ──
CREATE TABLE identity.verification_tokens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token      VARCHAR(255) UNIQUE NOT NULL,
    user_id    UUID         NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    type       VARCHAR(50)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_tokens_token ON identity.verification_tokens(token);

-- ── Backup codes (2FA) ──
CREATE TABLE identity.backup_codes (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    code_hash  VARCHAR(255) NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════
-- Seed roles + permissions
-- ═══════════════════════════════════════════════════════════

INSERT INTO identity.roles (role_name, description) VALUES
    ('FARMER',   'Farmer — sells crops via voice'),
    ('BUYER',    'Buyer — purchases crops via text/voice'),
    ('ADMIN',    'Administrator — manages all users'),
    ('DELIVERY', 'Delivery agent — handles logistics');

INSERT INTO identity.permissions (permission_name, description) VALUES
    ('user:read',     'Read user data'),
    ('user:write',    'Update user data'),
    ('user:delete',   'Delete users'),
    ('profile:read',  'Read own profile'),
    ('profile:write', 'Update own profile'),
    ('role:manage',   'Manage roles and permissions');

-- ═══════════════════════════════════════════════════════════
-- Role → Permission assignments
-- ═══════════════════════════════════════════════════════════

-- Step 1: ADMIN gets ALL permissions
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM identity.roles r
CROSS JOIN identity.permissions p
WHERE r.role_name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Step 2: FARMER and BUYER get profile permissions
--         (ADMIN already has all, so NOT included here — prevents duplicate PK)
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM identity.roles r
CROSS JOIN identity.permissions p
WHERE r.role_name IN ('FARMER','BUYER')
  AND p.permission_name IN ('profile:read','profile:write')
ON CONFLICT DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- Backfill existing users → user_roles
-- ═══════════════════════════════════════════════════════════

INSERT INTO identity.user_roles (user_id, role_id)
SELECT u.id, r.role_id
FROM identity.users u
JOIN identity.roles r ON r.role_name = u.role
ON CONFLICT DO NOTHING;