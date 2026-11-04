ALTER TABLE tb_field_action ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE tb_users ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_field_action_plot ON tb_field_action(plot_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_plot_property_lower_name ON tb_plot(property_id, lower(name));
