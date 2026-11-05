CREATE TABLE tb_plot_season (
    season_id uuid PRIMARY KEY,
    plot_id uuid NOT NULL REFERENCES tb_plot(plot_id) ON DELETE CASCADE,
    crop_name varchar(120) NOT NULL,
    variety varchar(120),
    planting_date date NOT NULL,
    harvest_date date,
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_season_dates CHECK (harvest_date IS NULL OR harvest_date >= planting_date)
);
CREATE UNIQUE INDEX uq_plot_active_season ON tb_plot_season(plot_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_plot_season_history ON tb_plot_season(plot_id, planting_date DESC);
INSERT INTO tb_plot_season(season_id, plot_id, crop_name, planting_date, status)
SELECT gen_random_uuid(), plot_id, crop_name, planting_date, 'ACTIVE'
FROM tb_plot WHERE crop_name IS NOT NULL AND trim(crop_name) <> '' AND planting_date IS NOT NULL;

ALTER TABLE tb_property_activity ADD COLUMN season_id uuid REFERENCES tb_plot_season(season_id) ON DELETE SET NULL;
ALTER TABLE tb_property_activity ADD COLUMN responsible_user_id uuid REFERENCES tb_users(user_id) ON DELETE SET NULL;
ALTER TABLE tb_property_activity ADD COLUMN responsible_name varchar(120);
ALTER TABLE tb_property_activity ADD COLUMN input_name varchar(120);
ALTER TABLE tb_property_activity ADD COLUMN input_quantity numeric(14,3);
ALTER TABLE tb_property_activity ADD COLUMN input_unit varchar(24);
ALTER TABLE tb_property_activity ADD COLUMN harvest_quantity numeric(14,3);
ALTER TABLE tb_property_activity ADD COLUMN harvest_unit varchar(24);
ALTER TABLE tb_property_activity ADD CONSTRAINT ck_activity_input_quantity CHECK (input_quantity IS NULL OR input_quantity > 0);
ALTER TABLE tb_property_activity ADD CONSTRAINT ck_activity_harvest_quantity CHECK (harvest_quantity IS NULL OR harvest_quantity > 0);
ALTER TABLE tb_property_activity DROP CONSTRAINT IF EXISTS tb_property_activity_activity_type_check;
ALTER TABLE tb_property_activity ADD CONSTRAINT ck_activity_type CHECK (activity_type IN ('IRRIGATION','RAINFALL','INSPECTION','INCIDENT','PLANTING','FERTILIZATION','SPRAYING','HARVEST','OTHER'));

CREATE TABLE tb_activity_photo (
    photo_id uuid PRIMARY KEY,
    activity_id uuid NOT NULL REFERENCES tb_property_activity(activity_id) ON DELETE CASCADE,
    file_name varchar(255) NOT NULL,
    content_type varchar(40) NOT NULL,
    image_data bytea NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_activity_photo_activity ON tb_activity_photo(activity_id);

ALTER TABLE tb_field_action ADD COLUMN assignee_user_id uuid REFERENCES tb_users(user_id) ON DELETE SET NULL;
ALTER TABLE tb_field_action ADD COLUMN completed_by_user_id uuid REFERENCES tb_users(user_id) ON DELETE SET NULL;
ALTER TABLE tb_field_action ADD COLUMN reminder_sent_at timestamp;
CREATE INDEX idx_field_action_reminder ON tb_field_action(status, due_date) WHERE reminder_sent_at IS NULL;
