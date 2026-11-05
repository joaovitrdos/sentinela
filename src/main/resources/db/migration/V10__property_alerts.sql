ALTER TABLE tb_property ADD COLUMN alert_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE tb_property ADD COLUMN alert_frost_temperature_c double precision NOT NULL DEFAULT 2
    CHECK (alert_frost_temperature_c BETWEEN -5 AND 10);
ALTER TABLE tb_property ADD COLUMN alert_heavy_rain_mm double precision NOT NULL DEFAULT 50
    CHECK (alert_heavy_rain_mm BETWEEN 10 AND 300);
ALTER TABLE tb_property ADD COLUMN alert_ndvi_drop double precision NOT NULL DEFAULT 0.15
    CHECK (alert_ndvi_drop BETWEEN 0.05 AND 0.8);
ALTER TABLE tb_property ADD COLUMN alert_river_radius_km double precision NOT NULL DEFAULT 30
    CHECK (alert_river_radius_km BETWEEN 1 AND 200);

CREATE TABLE tb_property_alert (
    alert_id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES tb_property (property_id) ON DELETE CASCADE,
    alert_key varchar(120) NOT NULL,
    alert_type varchar(24) NOT NULL CHECK (alert_type IN ('FROST', 'HEAVY_RAIN', 'INMET', 'RIVER', 'NDVI')),
    severity varchar(16) NOT NULL CHECK (severity IN ('ATENCAO', 'PERIGO')),
    title varchar(160) NOT NULL,
    message varchar(1000) NOT NULL,
    created_at timestamp NOT NULL,
    CONSTRAINT uk_property_alert_key UNIQUE (property_id, alert_key)
);

CREATE INDEX idx_property_alert_property_created ON tb_property_alert (property_id, created_at DESC);

COMMENT ON COLUMN tb_property.alert_enabled IS 'Indica se a propriedade participa da verificação automática de alertas.';
COMMENT ON COLUMN tb_property.alert_frost_temperature_c IS 'Temperatura mínima prevista, em °C, que gera alerta de geada.';
COMMENT ON COLUMN tb_property.alert_heavy_rain_mm IS 'Chuva diária prevista, em mm, que gera alerta de chuva forte.';
COMMENT ON COLUMN tb_property.alert_ndvi_drop IS 'Queda de NDVI entre duas observações que gera alerta de vegetação.';
COMMENT ON COLUMN tb_property.alert_river_radius_km IS 'Raio, em km, das estações de rio consideradas para a propriedade.';
COMMENT ON TABLE tb_property_alert IS 'Histórico de alertas gerados para as propriedades.';
COMMENT ON COLUMN tb_property_alert.alert_id IS 'Identificador do alerta.';
COMMENT ON COLUMN tb_property_alert.property_id IS 'Propriedade afetada pelo alerta.';
COMMENT ON COLUMN tb_property_alert.alert_key IS 'Chave do evento, usada para não repetir o mesmo alerta.';
COMMENT ON COLUMN tb_property_alert.alert_type IS 'Tipo do alerta: geada, chuva forte, aviso do INMET, rio ou NDVI.';
COMMENT ON COLUMN tb_property_alert.severity IS 'Gravidade do alerta.';
COMMENT ON COLUMN tb_property_alert.title IS 'Título do alerta.';
COMMENT ON COLUMN tb_property_alert.message IS 'Descrição do alerta.';
COMMENT ON COLUMN tb_property_alert.created_at IS 'Data e hora de geração do alerta.';
