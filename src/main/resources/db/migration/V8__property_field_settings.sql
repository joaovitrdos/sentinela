ALTER TABLE tb_property ADD COLUMN action_reminder_enabled boolean NOT NULL DEFAULT true;
ALTER TABLE tb_property ADD COLUMN action_reminder_days integer NOT NULL DEFAULT 1
    CHECK (action_reminder_days BETWEEN 0 AND 7);
ALTER TABLE tb_property ADD COLUMN require_season_for_field_records boolean NOT NULL DEFAULT false;
ALTER TABLE tb_property ADD COLUMN default_harvest_unit varchar(16) NOT NULL DEFAULT 'kg'
    CHECK (default_harvest_unit IN ('kg', 'sacas', 'toneladas'));
