-- Tábua de risco do ZARC (MAPA, dados abertos, CC-BY): janelas de plantio por município, cultura, ciclo, solo e manejo.
-- Os 36 decêndios do ano ficam num texto de 36 caracteres: 0 = fora da janela, 2/3/4 = risco de 20/30/40%.
CREATE TABLE tb_zarc_risk (
    zarc_risk_id bigserial PRIMARY KEY,
    harvest varchar(16) NOT NULL,
    crop_code varchar(20) NOT NULL,
    crop_name varchar(120) NOT NULL,
    cycle_code smallint NOT NULL,
    soil_code smallint NOT NULL,
    ibge_code integer NOT NULL,
    uf char(2) NOT NULL,
    municipality varchar(120) NOT NULL,
    municipality_key varchar(120) NOT NULL,
    climate_name varchar(60),
    management_code smallint NOT NULL,
    management_name varchar(60) NOT NULL,
    productivity varchar(40),
    management_level smallint,
    ordinance varchar(80),
    risks char(36) NOT NULL
);

CREATE INDEX ix_zarc_risk_municipality ON tb_zarc_risk (uf, municipality_key, crop_name);
CREATE INDEX ix_zarc_risk_harvest ON tb_zarc_risk (harvest);

CREATE TABLE tb_zarc_import (
    harvest varchar(16) PRIMARY KEY,
    source_url varchar(400) NOT NULL,
    source_modified varchar(60),
    row_count integer NOT NULL,
    imported_at timestamp NOT NULL
);

INSERT INTO tb_plan_feature (plan_code, feature) VALUES ('MEDIO', 'PLANTING_WINDOW'), ('PREMIUM', 'PLANTING_WINDOW');

COMMENT ON TABLE tb_zarc_risk IS 'Tábua de risco do Zoneamento Agrícola de Risco Climático (ZARC), importada dos dados abertos do MAPA.';
COMMENT ON COLUMN tb_zarc_risk.zarc_risk_id IS 'Identificador da linha.';
COMMENT ON COLUMN tb_zarc_risk.harvest IS 'Safra do arquivo de origem (ex.: 2026/2027) ou PERENE.';
COMMENT ON COLUMN tb_zarc_risk.crop_code IS 'Código da cultura zoneada.';
COMMENT ON COLUMN tb_zarc_risk.crop_name IS 'Nome da cultura zoneada.';
COMMENT ON COLUMN tb_zarc_risk.cycle_code IS 'Código do ciclo (13 perene, 19 semiperene, 20 a 26 grupos I a VI).';
COMMENT ON COLUMN tb_zarc_risk.soil_code IS 'Código do solo (1 arenoso, 2 textura média, 3 argiloso, 11 a 16 AD1 a AD6).';
COMMENT ON COLUMN tb_zarc_risk.ibge_code IS 'Geocódigo IBGE do município.';
COMMENT ON COLUMN tb_zarc_risk.uf IS 'Sigla da unidade federativa.';
COMMENT ON COLUMN tb_zarc_risk.municipality IS 'Nome do município como publicado pelo MAPA.';
COMMENT ON COLUMN tb_zarc_risk.municipality_key IS 'Nome do município sem acentos e em minúsculas, para comparar com a cidade da propriedade.';
COMMENT ON COLUMN tb_zarc_risk.climate_name IS 'Classe de clima do zoneamento.';
COMMENT ON COLUMN tb_zarc_risk.management_code IS 'Manejo (1 sequeiro, 2 irrigado, 3 irrigado com controle de geada).';
COMMENT ON COLUMN tb_zarc_risk.management_name IS 'Nome do manejo.';
COMMENT ON COLUMN tb_zarc_risk.productivity IS 'Classificação por produtividade, quando a portaria define.';
COMMENT ON COLUMN tb_zarc_risk.management_level IS 'Nível de manejo (NM1 a NM4), quando a portaria define.';
COMMENT ON COLUMN tb_zarc_risk.ordinance IS 'Portaria do ZARC publicada no D.O.U.';
COMMENT ON COLUMN tb_zarc_risk.risks IS 'Risco de cada decêndio do ano: 0 fora da janela, 2/3/4 para 20/30/40%.';
COMMENT ON TABLE tb_zarc_import IS 'Última importação de cada arquivo do ZARC.';
COMMENT ON COLUMN tb_zarc_import.harvest IS 'Safra importada.';
COMMENT ON COLUMN tb_zarc_import.source_url IS 'Endereço do arquivo nos dados abertos do MAPA.';
COMMENT ON COLUMN tb_zarc_import.source_modified IS 'Data de modificação informada pelo portal, para não importar de novo o mesmo arquivo.';
COMMENT ON COLUMN tb_zarc_import.row_count IS 'Quantidade de linhas importadas.';
COMMENT ON COLUMN tb_zarc_import.imported_at IS 'Momento da importação.';
