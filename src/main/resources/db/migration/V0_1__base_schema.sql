CREATE TABLE IF NOT EXISTS tb_users (
    user_id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    password varchar(255),
    picture varchar(512),
    google_id varchar(255),
    auth_provider varchar(20) NOT NULL,
    email_verified boolean NOT NULL DEFAULT false,
    token_version integer NOT NULL DEFAULT 0,
    created_at timestamp,
    updated_at timestamp,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_google_id UNIQUE (google_id)
);

CREATE TABLE IF NOT EXISTS tb_property (
    property_id uuid PRIMARY KEY,
    user_id uuid REFERENCES tb_users(user_id),
    name varchar(255) NOT NULL,
    longitude double precision NOT NULL,
    latitude double precision NOT NULL,
    city varchar(255) NOT NULL,
    state varchar(255) NOT NULL,
    polygon_json text,
    area_hectares double precision,
    crop_name varchar(120),
    plot_name varchar(120),
    planting_date date,
    crop_stage varchar(80),
    used boolean,
    created_at timestamp,
    update_at timestamp
);

CREATE TABLE IF NOT EXISTS tb_property_activity (
    activity_id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES tb_property(property_id),
    activity_type varchar(24) NOT NULL,
    activity_date date NOT NULL,
    notes varchar(1000) NOT NULL,
    amount_mm double precision,
    created_at timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_emails (
    email_id uuid PRIMARY KEY,
    user_id uuid,
    email_from varchar(255),
    email_to varchar(255),
    subject varchar(255),
    status_email varchar(255),
    type_email varchar(255),
    created_at timestamp
);

CREATE TABLE IF NOT EXISTS tb_verification_code (
    verification_code_id uuid PRIMARY KEY,
    code varchar(255),
    purpose varchar(24) NOT NULL,
    target_email varchar(255),
    expires_at timestamp,
    used boolean,
    user_id uuid REFERENCES tb_users(user_id),
    created_at timestamp
);

CREATE TABLE IF NOT EXISTS tb_system (
    system_id uuid PRIMARY KEY,
    version varchar(255) NOT NULL,
    maintenance boolean NOT NULL,
    maintenance_start timestamp,
    maintenance_end timestamp
);

CREATE INDEX IF NOT EXISTS idx_property_user ON tb_property(user_id);
CREATE INDEX IF NOT EXISTS idx_property_activity_property_date ON tb_property_activity(property_id, activity_date);
CREATE INDEX IF NOT EXISTS idx_email_user ON tb_emails(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_code_user ON tb_verification_code(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_code_created ON tb_verification_code(created_at);
