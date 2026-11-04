# Relatórios Jasper

O `ReportService` é o executor compartilhado: localiza um `ReportProvider` registrado, compila/cacheia o JRXML, preenche o relatório e exporta o formato solicitado. Cada provider valida os próprios parâmetros e prepara a fonte de dados adequada.

## Código e arquivos

Use o padrão `{integracao}_{recurso}_{relatorio}` em minúsculas, sem acentos:

- `snirh_hydro_levels_station_status`: situação dos níveis por estação do SNIRH.
- `snirh_rainfall_24h_by_station`: chuva acumulada nas últimas 24 horas por estação, consultada no SNIRH.
- `weather_time_zone_local_time`: relatório futuro de fusos/horários da WeatherAPI.
- `sentinela_properties_activities`: relatório de dados próprios do Sentinela.
- `sentinela_activity_summary_by_property`: quantidade e dias de atividade por propriedade e categoria, com lâminas registradas para irrigação.
- `sentinela_irrigation_monthly_summary`: registros mensais de irrigação, dias irrigados e estatísticas das lâminas informadas.

O `reportCode` da API é resolvido por uma lista de providers Spring; ele nunca vira um caminho de arquivo recebido do navegador. O caminho do JRXML fica fixo no provider. Os templates ficam em `src/main/resources/reports/{integracao}/{recurso}/{reportCode}.jrxml`.

## Fontes e parâmetros

- Dados de integração REST, como o SNIRH, são consultados pelo provider e entregues ao JRXML por `JRMapCollectionDataSource`.
- Relatórios de tabelas SQL podem declarar a consulta no JRXML. Nesse caso o provider retorna os parâmetros tipados e o `ReportService` preenche o relatório com a conexão JDBC.
- Use `$P{PARAMETRO}` para valores SQL vinculados. Não receba SQL, `$P!{...}` ou caminho de template do React.
- A configuração global do Jasper desativa `$P!{...}` para impedir que parâmetros alterem o texto da consulta em tempo de execução.
- Identificadores de usuário, empresa e filial devem ser obtidos/validados no backend, nunca confiados ao payload do navegador.

Todos os templates devem ser salvos e compilados com JasperReports 7.0.x. Para editar, use Jaspersoft Studio 7 ou posterior.
