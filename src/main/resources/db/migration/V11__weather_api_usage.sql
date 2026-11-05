CREATE TABLE tb_weather_api_usage (
    usage_month varchar(7) PRIMARY KEY,
    calls bigint NOT NULL
);

COMMENT ON TABLE tb_weather_api_usage IS 'Consumo mensal de chamadas à WeatherAPI.';
COMMENT ON COLUMN tb_weather_api_usage.usage_month IS 'Mês do consumo, no formato AAAA-MM.';
COMMENT ON COLUMN tb_weather_api_usage.calls IS 'Quantidade de chamadas feitas no mês.';
