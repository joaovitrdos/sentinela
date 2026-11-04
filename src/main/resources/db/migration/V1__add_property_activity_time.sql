DO $$
BEGIN
    IF to_regclass('tb_property_activity') IS NOT NULL THEN
        ALTER TABLE tb_property_activity
            ADD COLUMN IF NOT EXISTS activity_time TIME;
    END IF;
END
$$;
