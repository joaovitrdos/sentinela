CREATE TABLE tb_plot (
    plot_id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES tb_property(property_id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    crop_name varchar(120),
    planting_date date,
    crop_stage varchar(80),
    area_hectares double precision,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plot_property_name UNIQUE (property_id, name),
    CONSTRAINT ck_plot_area CHECK (area_hectares IS NULL OR area_hectares > 0)
);

INSERT INTO tb_plot (plot_id, property_id, name, crop_name, planting_date, crop_stage, area_hectares)
SELECT gen_random_uuid(), property_id, trim(plot_name), crop_name, planting_date, crop_stage, area_hectares
FROM tb_property
WHERE plot_name IS NOT NULL AND trim(plot_name) <> '';

ALTER TABLE tb_property_activity ADD COLUMN plot_id uuid REFERENCES tb_plot(plot_id) ON DELETE SET NULL;
ALTER TABLE tb_property_activity ADD CONSTRAINT ck_activity_amount_nonnegative CHECK (amount_mm IS NULL OR amount_mm >= 0);
CREATE INDEX idx_property_activity_plot_date ON tb_property_activity(plot_id, activity_date);

UPDATE tb_property_activity activity
SET plot_id = plot.plot_id
FROM tb_plot plot
WHERE plot.property_id = activity.property_id;

CREATE TABLE tb_field_action (
    action_id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES tb_property(property_id) ON DELETE CASCADE,
    plot_id uuid REFERENCES tb_plot(plot_id) ON DELETE SET NULL,
    kind varchar(24) NOT NULL CHECK (kind IN ('INSPECTION', 'IRRIGATION')),
    title varchar(160) NOT NULL,
    responsible_name varchar(120) NOT NULL,
    due_date date NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE')),
    source_note varchar(500),
    completion_note varchar(1000),
    completed_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_field_action_property_due ON tb_field_action(property_id, status, due_date);
