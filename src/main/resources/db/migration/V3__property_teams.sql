ALTER TABLE tb_property ADD COLUMN IF NOT EXISTS team_enabled boolean NOT NULL DEFAULT false;

CREATE TABLE tb_property_member (
    member_id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES tb_property(property_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES tb_users(user_id) ON DELETE CASCADE,
    role varchar(16) NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN')),
    joined_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_property_member UNIQUE (property_id, user_id)
);
CREATE INDEX idx_property_member_user ON tb_property_member(user_id);

CREATE TABLE tb_property_invitation (
    invitation_id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES tb_property(property_id) ON DELETE CASCADE,
    email varchar(320) NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN')),
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamp NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_property_invitation_email UNIQUE (property_id, email)
);
