# Plano de evolução do Sentinela

> Status: **planejamento, nada implementado.** Data: 09/11/2026.
> Confiança das afirmações: **[Certo]** evidência sólida · **[Provável]** inferência forte · **[Chute]** hipótese a validar.

---

## 1. O que foi pedido

1. Pesquisar exemplos, mapas, ideias, funções e jeitos de agregar valor ao produto.
2. Deixar de ser uma ferramenta **só de mostrar dados** e passar a entregar **previsão, probabilidade, decisão e histórico antigo**.
3. Ter algo que a pessoa **bate o olho e pensa "deixa eu ver como funciona… vou assinar"**.
4. Foco em **agricultura / agropecuária** (e dizer se isso estiver errado).
5. Reorganizar **abas e nomenclatura** (ex.: "Início" não descreve uma tela de mapa com funções).
6. Olhar **SaaS e aplicativos parecidos** e aproveitar o que fizer sentido.
7. **Regra obrigatória:** toda API precisa ser validada como **gratuita**; se for paga, **não entra**.
8. Usar o **Codex como agente** para dividir as tarefas com o Claude.

---

## 2. O foco em agro está certo?

**Sim, mas "agro" sozinho é amplo demais e coloca o Sentinela contra gigantes que dão o app de graça.** [Provável]

- Climate FieldView (Bayer), xarvio (BASF) e Cropwise (Syngenta) são distribuídos junto com venda de insumos e máquinas. Competir em "gestão completa da fazenda" ou "dados de máquina" é perder.
- Aegro e similares dominam **gestão financeira e operacional**. Também não é a nossa briga.
- O que o Sentinela já tem e os outros não destacam: **água + clima + rios/estações da ANA + histórico**, sem hardware, por R$ 20–120/mês.

**Recorte recomendado (público que paga):**

| Público | Por que paga | Plano provável |
|---|---|---|
| Produtor pequeno/médio de grãos, café, cana, hortifrúti | Decidir plantar, pulverizar, irrigar e colher com menos risco | Normal / Médio |
| Pecuarista (corte e leite) | Pastagem, água e estresse térmico do gado | Médio |
| **Consultor agronômico / técnico de cooperativa** | Atende várias fazendas, precisa de laudo e relatório para o cliente | **Premium** (sem limite de propriedades) |

O consultor é o melhor comprador do Premium [Provável]: ele cobra do cliente e o Sentinela vira ferramenta de trabalho.

---

## 3. Regra das APIs: o que pode e o que não pode

O Sentinela é **SaaS pago**, ou seja, **uso comercial**. Por isso, "grátis para uso não comercial" **não serve**.

### 3.1 Permitidas (gratuitas e com uso comercial permitido)

| Fonte | O que entrega | Licença / condição | Confiança |
|---|---|---|---|
| **NASA POWER** (já usado) | Clima diário desde 1981, ET0, radiação | Sem restrição, pede citação | [Certo] |
| **CHIRPS (UCSB)** | Chuva diária 5 km desde 1981 | Domínio público (CC0), pede citação | [Certo] |
| **ECMWF Open Data** | Previsão IFS/AIFS até 15 dias, **inclui ensemble** (base para probabilidade) | CC-BY-4.0, atribuição obrigatória | [Certo] |
| **Copernicus C3S (CDS API)** | Previsão **sazonal** (3–6 meses) | Licença Copernicus, gratuita, exige cadastro e aceite por dataset | [Provável] |
| **ZARC – Tábua de risco (MAPA, dados abertos)** | Janelas de plantio por município, cultura, solo e risco (20/30/40%) | CSV, Creative Commons Attribution | [Certo] |
| **IBGE SIDRA** | Produção e produtividade por município (PAM) e rebanhos (PPM) | Dados públicos | [Provável] confirmar tabelas |
| **Banco Central (SGS)** | Dólar, Selic | Dados públicos | [Provável] |
| **ANA / SNIRH** (já usado) | Rios, chuva e níveis das estações | Dados públicos | [Certo] |
| **INMET** (já usado) | Previsão e estações oficiais | Dados públicos | [Provável] |
| **SICAR / CAR** (já usado) | Contorno do imóvel | Dados públicos | [Certo] |
| **INPE Queimadas** | Focos de calor desde 1998 | Dados abertos (arquivos, WMS/WFS) | [Provável] confirmar endpoint |
| **NOAA CPC / IRI** | Situação e probabilidade de El Niño / La Niña | Governo dos EUA, público | [Provável] confirmar termos |
| **SoilGrids (ISRIC)** | Textura e propriedades do solo | CC-BY-4.0. **Usar WCS/download**, a API REST é beta e pode estar pausada | [Provável] |

### 3.2 Proibidas (pagas ou sem uso comercial gratuito)

| Fonte | Motivo | Alternativa |
|---|---|---|
| **Open-Meteo (API hospedada)** | Grátis **só para uso não comercial**. Comercial exige assinatura | NASA POWER + CHIRPS + ECMWF Open Data |
| **Embrapa AgroAPI (Agritec, Agrofit etc.)** | Só versão de teste. O uso contínuo é cobrado por volume | CSV da Tábua de risco do ZARC |
| **B3 (futuros agrícolas)** | Dado pago | Não usar |
| **CEPEA** | Sem API oficial. Raspar o site tem risco legal | Não usar até existir licença clara |
| **WhatsApp Business API** | Cobrança por conversa | E-mail, Web Push e bot do Telegram (gratuitos) |

### 3.3 Verificar antes de usar

| Fonte | Dúvida |
|---|---|
| **MapBiomas Alerta** | Dados públicos e gratuitos, mas **não encontrei cláusula de uso comercial** da API |
| **Conab (preços)** | Sem API. Há só downloads no portal, e a licença não está clara |

### 3.4 Atenção ao que já existe no produto

- **NDVI pelo Sentinel Hub do Copernicus Data Space (checado em 09/11/2026):**
  - Os dados Sentinel são "free, full and open"; o uso comercial é permitido com crédito ao Copernicus (resposta da equipe do CDSE no fórum). [Provável]
  - A cota grátis por conta é de **10.000 requisições e 10.000 unidades de processamento por mês** (300 por minuto). Acima disso, só a oferta paga (Sentinel Hub no CREODIAS). [Certo]
  - Os termos **proíbem criar várias contas** para fugir da cota. [Certo]
  - **Conclusão:** o problema não é licença, é **escala**. Com cache por propriedade e cena, a cota aguenta algumas centenas de propriedades ativas [Chute]. Antes de passar disso, migrar para as cenas Sentinel-2 L2A abertas (COGs públicos) processadas no backend.
  - Fontes: [Termos CDSE](https://dataspace.copernicus.eu/terms-and-conditions) · [Cotas](https://documentation.dataspace.copernicus.eu/Quotas.html) · [Fórum: uso comercial](https://forum.dataspace.copernicus.eu/t/commercial-use-of-the-data-from-api/736)
- **WeatherAPI (plano grátis):** já foi ajustado (crédito, aviso e limite). Manter, mas usar a ECMWF como fonte principal da **probabilidade**, porque o plano grátis da WeatherAPI não traz ensemble.

**Regra de processo:** toda fonte nova precisa ter, no PR, um link para os termos, a data da checagem e uma linha nesta seção.

---

## 4. O que os concorrentes fazem (e o que vale copiar)

| Produto | Ponto forte | O que aproveitar |
|---|---|---|
| **xarvio Field Manager (BASF)** | "Spray Timer": junta fase da cultura, previsão e modelo de doença e diz **quando** aplicar. Zonas por **15 anos de biomassa** | Recomendação com motivo e momento certo; potencial por histórico de NDVI |
| **Climate FieldView (Bayer)** | Dados de máquina, mapas de produtividade, comparação histórica | Comparar safras do mesmo talhão |
| **Agrosmart** | Clima, irrigação e risco climático no Brasil | Risco climático por fase da cultura |
| **Aegro** | Gestão financeira e operacional, integração com FieldView | **Não copiar** (fora do nosso foco) |
| **OneSoil** | NDVI grátis e zonas de manejo | Mapa de NDVI fácil de ler; comparação entre talhões |
| **Plantio Certo (Embrapa/MAPA)** | Datas de plantio do ZARC no celular | O mesmo dado, **cruzado com a previsão e a umidade do solo de agora** |

**Diferencial do Sentinela:** não ser "mais um mapa". Ser um app que **responde perguntas** como "planto esta semana?", "pulverizo amanhã?", "este ano está pior que o normal?" e "qual a chance de chover 20 mm até sexta?".

---

## 5. Nova navegação e nomenclatura

### 5.1 Problema atual

| Hoje | O que a tela faz | Problema |
|---|---|---|
| Início | Mapa do Brasil com chuva, rios, estações e clima | "Início" não diz que é um mapa regional |
| Propriedades | Mapa e cadastro das fazendas | OK |
| Dashboard | Painel com indicadores | Termo em inglês, não diz o que mostra |
| Relatórios | Exportação de dados | OK, mas o histórico ficaria escondido aqui |

### 5.2 Proposta de menu superior

| Aba | Conteúdo | Substitui |
|---|---|---|
| **Hoje** | Painel de decisão: o que fazer hoje em cada propriedade, alertas e próximos 7 dias | Dashboard |
| **Propriedades** | Fazendas, talhões e contornos (CAR) | Propriedades |
| **Mapa regional** | Chuva, rios, estações, focos de calor e clima do Brasil | Início |
| **Previsões** | Probabilidade de chuva, sazonal, El Niño/La Niña, janela de plantio | Nova |
| **Histórico e relatórios** | 40 anos de clima, safra × clima, comparativos e exportação | Relatórios |

- Depois do login, a tela inicial passa a ser **Hoje** (`/hoje`). O mapa regional deixa de ser a "home".
- As rotas antigas (`/home`, `/dashboard`) redirecionam para as novas, para não quebrar favoritos.

### 5.3 Abas dentro de uma propriedade

**Visão geral · Clima · Lavoura (NDVI) · Manejo (irrigação, pulverização, atividades) · Histórico · Equipe**

### 5.4 Outros nomes

| Atual | Sugestão | Motivo |
|---|---|---|
| Planos Normal / Médio / Premium | **Essencial / Produtor / Consultor** | Diz para quem é cada plano [Chute] validar com usuários |
| "Janela de pulverização" | **Pode pulverizar?** | Pergunta direta, como o usuário pensa |
| "Recomendação de irrigação" | **Quanto irrigar** | Idem |
| "Alertas" | **Avisos da fazenda** | Mais próximo do vocabulário do campo [Chute] |

Toda mudança de nome passa pelos 3 idiomas (`pt-BR`, `en`, `es`).

---

## 6. Funcionalidades propostas

Legenda de esforço: **P** pequeno (até 3 dias) · **M** médio (1–2 semanas) · **G** grande (3+ semanas).

### 6.1 Gancho de venda: "Raio-X da fazenda" (página pública, sem login)

**É o "bate o olho e quero ver como funciona".**

1. A pessoa clica no mapa, desenha a área ou digita o **código do CAR**.
2. Em cerca de 10 segundos, aparece um resumo:
   - **Chuva dos últimos 40 anos** no ponto (CHIRPS/NASA POWER), com "este mês está X% abaixo da média".
   - **Janela de plantio do ZARC** para soja e milho no município, com o risco.
   - **Chance de chover ≥ 10/20/50 mm nos próximos 7 e 15 dias** (ensemble da ECMWF).
   - **Situação El Niño / La Niña** e o que isso costuma significar na região.
   - **Focos de calor** num raio de 10 km nos últimos 30 dias.
3. Chamada final: "**Receba esses avisos todo dia: 7 dias grátis.**"

| Fonte | Esforço | Plano |
|---|---|---|
| CHIRPS, NASA POWER, ZARC, ECMWF, NOAA CPC, INPE | G | Público (captação) |

**Cuidados:**
- Cache agressivo por ponto e município, para não estourar limites.
- Limite de consultas por IP.
- Sem guardar dado pessoal.

### 6.2 Previsão e probabilidade

| # | Funcionalidade | Como funciona | Fonte | Esforço | Plano |
|---|---|---|---|---|---|
| 1 | **Probabilidade de chuva acumulada** | "70% de chance de chover mais de 20 mm até sexta", calculado pelos membros do ensemble | ECMWF Open Data | M | Produtor |
| 2 | **Perspectiva dos próximos 3 meses** | Tercis: acima, dentro ou abaixo do normal para chuva e temperatura | C3S sazonal | M | Consultor |
| 3 | **El Niño / La Niña** | Situação atual, probabilidade por trimestre e impacto histórico na região (anos ENSO × chuva CHIRPS do ponto) | NOAA CPC/IRI + CHIRPS | M | Produtor |
| 4 | **Risco de geada** | Previsão de mínima + frequência histórica de geada por decêndio no ponto | ECMWF + NASA POWER | P | Produtor |
| 5 | **Estimativa de colheita** | Graus-dia acumulados desde o plantio → fase estimada e data provável de colheita | NASA POWER + ECMWF | M | Produtor |

**Regra de comunicação:** toda previsão mostra a **probabilidade** e a **fonte**, nunca uma certeza.

### 6.3 Decisão (o coração da aba "Hoje")

| # | Funcionalidade | Resposta que entrega | Fonte | Esforço | Plano |
|---|---|---|---|---|---|
| 6 | **Cartões "O que fazer hoje"** | Semáforo por propriedade (pulverizar, irrigar, plantar, colher, trabalhar com o gado), **sempre com o motivo** | Motores abaixo | M | Essencial (limitado) / Produtor |
| 7 | **Janela de plantio inteligente** | ZARC + umidade do solo + chuva prevista → "plantar entre 12 e 20/11; risco ZARC 20%" | ZARC + ECMWF + balanço hídrico | M | Produtor |
| 8 | **Balanço hídrico do solo** (evolui o `IrrigationService`) | Água disponível por talhão: ET0 × Kc da fase − chuva → "faltam 18 mm; irrigar 2 dias" | NASA POWER, ECMWF, CHIRPS, SoilGrids (solo) | M | Produtor |
| 9 | **Risco de doença** | Ferrugem asiática (soja), ferrugem do café, requeima: horas de molhamento foliar + temperatura, por modelos publicados (Embrapa e literatura) | ECMWF (umidade, temperatura) | G | Consultor |
| 10 | **Pode pulverizar?** (evolui o `SprayWindowService`) | Já existe. Somar a chance de chuva nas próximas 6 h, vento, ΔT e inversão térmica | ECMWF + INMET | P | Produtor |

**Aviso obrigatório no risco de doença:** é uma estimativa e não substitui o agrônomo.

### 6.4 Histórico antigo

| # | Funcionalidade | Exemplo | Fonte | Esforço | Plano |
|---|---|---|---|---|---|
| 11 | **40 anos de clima da propriedade** | "3º outubro mais seco desde 1981"; gráfico do ano atual × média × anos extremos | CHIRPS + NASA POWER | M | Essencial |
| 12 | **Safra × clima do município** | "Nos anos com chuva assim em dezembro, a soja do município rendeu 12% menos" | IBGE PAM + CHIRPS | M | Consultor |
| 13 | **Histórico de NDVI por safra** | Comparar a curva do talhão nesta safra com as anteriores (desde 2017) | Sentinel-2 (ver 3.4) | M | Produtor |
| 14 | **Focos de calor históricos** | Recorrência de queimadas no entorno desde 1998 | INPE | P | Produtor |

### 6.5 Monitoramento e avisos

| # | Aviso | Fonte | Esforço |
|---|---|---|---|
| 15 | Foco de calor a menos de X km da propriedade | INPE | P |
| 16 | Queda anormal de NDVI no talhão (falha, praga, seca) | Sentinel-2 | M |
| 17 | Chance alta de chuva forte, granizo ou geada | ECMWF | P |
| 18 | Rio abaixo da cota de referência (já existe; ligar à captação de irrigação) | ANA | P |

**Canais gratuitos:** e-mail, Web Push (PWA) e bot do Telegram. **Não usar WhatsApp** (pago).

### 6.6 Pecuária

| # | Funcionalidade | Fonte | Esforço | Confiança |
|---|---|---|---|---|
| 19 | **Estresse térmico do gado (ITU)** por hora nos próximos dias: "evitar manejo das 11h às 16h" | ECMWF / NASA POWER | P | [Certo] método conhecido |
| 20 | **Condição da pastagem** por NDVI e tendência da área | Sentinel-2 | M | [Provável] |
| 21 | **Estimativa de lotação (UA/ha)** a partir de NDVI + chuva | Sentinel-2 + CHIRPS | G | [Chute] precisa de calibração |

### 6.7 Para o consultor e o crédito rural

| # | Funcionalidade | Por que vende | Esforço | Plano |
|---|---|---|---|---|
| 22 | **Laudo agroclimático em PDF** por propriedade: histórico de chuva, ZARC, NDVI, focos de calor, CAR | Banco, seguro rural e cooperativa pedem esse tipo de informação | M | Consultor |
| 23 | **Painel multi-cliente** com ranking de risco ("quais clientes visitar primeiro esta semana") | Economiza deslocamento do consultor | M | Consultor |
| 24 | **Relatório semanal automático** por e-mail para o produtor, com a marca do consultor | Fideliza o cliente final | M | Consultor |

### 6.8 Mercado (limitado pela regra das APIs)

| # | Funcionalidade | Fonte | Situação |
|---|---|---|---|
| 25 | Dólar e Selic no painel | BCB SGS | Pode entrar |
| — | Preço de soja, milho e boi | CEPEA, B3 e Conab | **Fora** até existir fonte gratuita com licença clara |

### 6.9 Experiência

| # | Item | Esforço |
|---|---|---|
| 26 | **PWA instalável e com cache offline** dos últimos dados (o campo tem pouco sinal) | M |
| 27 | **Tour guiado no 1º acesso:** cadastrar a fazenda pelo CAR e ver o primeiro "Hoje" em menos de 2 minutos | P |
| 28 | **Linguagem do campo:** "mm", "sacas/ha", "decêndio", "safrinha"; evitar jargão técnico em inglês | P |

---

## 7. Novo desenho dos planos

| Recurso | Essencial (R$ 20) | Produtor (R$ 60) | Consultor (R$ 120) |
|---|---|---|---|
| Propriedades | 1 | 5 | Ilimitadas |
| Hoje (cartões de decisão) | Básico | Completo | Completo + multi-cliente |
| 40 anos de clima | ✓ | ✓ | ✓ |
| Probabilidade de chuva, geada, El Niño/La Niña | — | ✓ | ✓ |
| Janela de plantio, balanço hídrico, Pode pulverizar? | — | ✓ | ✓ |
| NDVI e histórico por safra | — | ✓ | ✓ |
| Avisos (e-mail, push, Telegram) | Só e-mail | ✓ | ✓ |
| Previsão sazonal, risco de doença, safra × clima | — | — | ✓ |
| Laudo PDF, relatório com marca própria, equipe | — | Equipe | ✓ |
| Novidades primeiro | — | — | ✓ |

Os valores e o teste grátis de 7 dias continuam como estão. As regras continuam **somente no backend** (`tb_plan_feature`), com novos itens no enum `Feature`.

---

## 8. Ordem de execução

### Fase 0: Higiene (antes de qualquer feature)
1. ✅ Validar os termos do Sentinel Hub/CDSE (seção 3.4): o NDVI fica, com limite de escala anotado.
2. ✅ (parcial) Renomear abas e rotas: "Início" virou **Mapa regional** (`/mapa`, com `/home` redirecionando) e "Dashboard" virou **Painel**. A aba **Hoje** (`/hoje`) só entra junto com os cartões de decisão (item 8), para não ter nome sem conteúdo.
3. ✅ (início) Camada de clima: `DataSourceDto` leva a fonte e a licença em cada resposta. O primeiro serviço é o `ClimateHistoryService`. Os próximos clientes (CHIRPS, ECMWF) entram no mesmo formato.

### Andamento
- ✅ Backend do item 5 (40 anos de clima): `GET /api/properties/{id}/climate-history` (plano BASE). Uma chamada à NASA POWER desde 1981 (~800 KB, ~2 s, cache de 6 h). Devolve os últimos 30 dias e os últimos 12 meses comparados com o mesmo trecho de cada ano (anomalia em % e posição de "mais seco"), além das médias e extremos de cada mês.
- ✅ Tela do histórico na propriedade (`property-climate-history.tsx`): cartão dos últimos 30 dias (chuva, % da média, posição no ranking) e gráfico de 12 meses × média, com o ranking de cada mês no tooltip. Validada no navegador com dados reais de Cuiabá: out/2026 com 39% abaixo da média (6º mais seco em 46 anos) e jan/2026 como o mais seco da série.
- ✅ Padronização do backend: toda checagem de nulo/vazio usa `Util`, e todo log usa `LogUtil`.
- ✅ Janela de plantio do ZARC (item 7, sem o cruzamento com a previsão):
  - Migration `V16` (tabela `tb_zarc_risk`, compacta: os 36 decêndios num texto) e recurso `PLANTING_WINDOW` nos planos MEDIO e PREMIUM.
  - `ZarcImportService`: importa uma vez por dia e só baixa de novo quando o portal muda a data do arquivo. Configurados: safra 2026/2027 (211 MB, 957 mil linhas) e perenes. A safra 2027/2028 entra em `application.yaml` quando o MAPA publicar.
  - O portal do MAPA **derruba conexões sem identificação**: o importador se apresenta como `Sentinela-Importador/1.0 (+https://techlayoof.tech)`. Funciona assim (checado em 09/11/2026).
  - Leitor validado contra o arquivo real inteiro: 957.490 linhas, nenhuma rejeitada.
  - Tela na propriedade: cultura, manejo, ciclo e solo, faixa dos 36 decêndios por risco e a frase "Janela aberta: plante até…" ou "Próxima janela: de… a…".
  - ⚠️ A gravação no Postgres (957 mil linhas numa transação) não foi testada aqui, porque esta máquina não tem Docker. Conferir o primeiro import em produção pelo log.
- ⏳ Probabilidade de chuva (item 6) com o Codex, no worktree `sentinela-codex-ecmwf`. Os arquivos da ECMWF usam compressão CCSDS (template 5.42), a parte mais difícil.

### Fase 1: Maior impacto de venda
4. Raio-X público da fazenda (6.1).
5. 40 anos de clima da propriedade (11).
6. Probabilidade de chuva acumulada (1).
7. Janela de plantio com ZARC (7).
8. Aba **Hoje** com os cartões de decisão (6).

### Fase 2: Decisão diária
9. Balanço hídrico (8) e a melhoria do "Pode pulverizar?" (10).
10. Geada (4), ITU do gado (19) e El Niño/La Niña (3).
11. Avisos de focos de calor, NDVI e clima (15–17), com Web Push e Telegram.

### Fase 3: Premium/Consultor
12. Laudo PDF (22), painel multi-cliente (23) e relatório com marca (24).
13. Previsão sazonal (2), safra × clima (12) e risco de doença (9).
14. Pastagem (20) e PWA offline (26).

---

## 9. Divisão de trabalho: Claude × Codex

### 9.1 Papéis

| Claude (orquestra e revisa) | Codex (executa tarefas fechadas) |
|---|---|
| Arquitetura, contratos de API e modelo de dados | Clientes HTTP de cada fonte (NASA POWER, CHIRPS, ECMWF, ZARC, IBGE, BCB, INPE, NOAA) |
| Motores de decisão (balanço hídrico, janela de plantio, risco) e as regras de plano | Importadores de CSV (ZARC, IBGE) e migrations de carga |
| Textos, nomenclatura e i18n nos 3 idiomas | Componentes de tela a partir de especificação pronta |
| Revisão de todo diff do Codex, testes de integração e validação no navegador | Testes unitários dos clientes e parsers |
| Checagem de licença de cada API (seção 3) | Ajustes mecânicos (renomear rotas, redirecionamentos) |

### 9.2 Como dividir sem conflito
1. Cada tarefa vira um item com: **objetivo, arquivos permitidos, contrato de entrada/saída e critério de aceite** (testes que precisam passar).
2. O Codex trabalha em um **branch/worktree separado** por tarefa (habilidades `codex-dispatch` ou `orchestration`/Orca já disponíveis).
3. O Claude revisa o diff (correção, licença da fonte, i18n e regra de plano no backend) antes de juntar.
4. **Ninguém faz commit sem autorização do dono do projeto.** A mensagem de commit é entregue para ele aprovar.
5. Paralelizar só tarefas **independentes**: por exemplo, o Codex escreve os clientes CHIRPS e ZARC enquanto o Claude desenha o motor de balanço hídrico.

### 9.3 Exemplo de primeira rodada (Fase 1)

| Tarefa | Quem |
|---|---|
| Contrato da "camada de clima" e cache | Claude |
| Cliente CHIRPS (série diária por ponto) + testes | Codex |
| Importador do CSV da Tábua de risco do ZARC + testes | Codex |
| Leitura do ensemble ECMWF (GRIB2 via NetCDF-Java, open source) | Codex, com revisão cuidadosa do Claude |
| Cálculo de probabilidade e histórico de 40 anos | Claude |
| Tela "Raio-X" a partir do layout definido | Codex |
| Textos, i18n, validação no navegador e mensagem de commit | Claude |

---

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Fonte pública fora do ar ou mudando formato | Cache local, *fallback* entre fontes e alerta de falha |
| Arquivos grandes (GRIB2 da ECMWF, CHIRPS) | Recortar só a região do Brasil e processar em job agendado, não por requisição |
| Usuário tratar estimativa como certeza | Sempre mostrar a probabilidade, a fonte e o aviso; nunca prometer produtividade |
| Termos de uso mudarem | Revisão trimestral da seção 3, com data |
| Escopo grande demais | Seguir as fases; a Fase 1 sozinha já muda a percepção do produto |
| LGPD na página pública | Não guardar consulta com dado pessoal; limitar por IP |

---

## 11. Pendências para validar antes de codar

- [ ] Termos do CDSE, capítulo 3: uso comercial do Sentinel Hub na cota grátis.
- [ ] Endpoint e formato oficiais dos dados abertos do INPE Queimadas.
- [ ] Termos do NOAA CPC e do IRI para as probabilidades de ENSO.
- [ ] Tabelas exatas do SIDRA (PAM e PPM) e o nível municipal.
- [ ] Uso comercial do MapBiomas Alerta.
- [ ] Volume e frequência do ECMWF Open Data necessários (só Brasil, só as variáveis usadas).
- [ ] Validar com 3 a 5 produtores e consultores os nomes das abas e dos planos (seções 5 e 7).

---

## 12. Fontes consultadas

- NASA POWER: <https://www.earthdata.nasa.gov/learn/articles/power-overview> · <https://registry.opendata.aws/nasa-power/>
- CHIRPS: <https://chc.ucsb.edu/data/chirps> · <https://developers.google.com/earth-engine/datasets/catalog/UCSB-CHG_CHIRPS_DAILY>
- ECMWF Open Data: <https://ecmwf.int/en/forecasts/datasets/open-data>
- C3S sazonal: <https://www.ecmwf.int/en/forecasts/datasets/c3s-seasonal-forecasts>
- ZARC, Tábua de risco: <https://dados.agricultura.gov.br/en/dataset/tabua-de-risco-zoneamento-agricola-de-risco-climatico/resource/95588752-a068-415b-ab2f-55043eb7f9ac>
- IBGE agregados (SIDRA): <https://servicodados.ibge.gov.br/api/docs/agregados?versao=3>
- Banco Central, dados abertos: <https://dadosabertos.bcb.gov.br/ca/dataset/10813-taxa-de-cambio---livre---dolar-americano-compra>
- INPE Queimadas: <https://dataserver-coids.inpe.br/queimadas/queimadas/riscofogo_meteorologia/documentos/Processo-gera-operacional-dados-ambientais-Observado&Previsto.pdf>
- IRI ENSO: <https://iri.columbia.edu/our-expertise/climate/forecasts/enso/2025-April-quick-look/>
- SoilGrids: <https://docs.isric.org/globaldata/soilgrids/SoilGrids_faqs_02.html>
- MapBiomas Alerta API: <https://plataforma.alerta.mapbiomas.org/api/docs/index.html>
- Open-Meteo (termos e preços): <https://open-meteo.com/en/terms> · <https://open-meteo.com/en/pricing>
- Embrapa AgroAPI: <https://www.agroapi.cnptia.embrapa.br/portal/assets/docs/agroapi-primeiros-passos.pdf> · <https://revistacultivar.com.br/noticias/aplicativo-movel-facilita-o-acesso-as-informacoes-do-zoneamento-agricola-de-risco-climatico>
- CEPEA e Conab: <https://www.gov.br/conab/pt-br/acesso-a-informacao/dados-abertos> · <https://pypi.org/project/agrobr/>
- Copernicus Data Space (cotas): <https://dataspace.copernicus.eu/about> · <https://documentation.dataspace.copernicus.eu/Quotas.html>
- xarvio: <https://global.xarvio.com/field-manager/crop-protection> · <https://www.xarvio.com/ca/en/products/field-manager/field-monitor.html>
- Climate FieldView e Aegro: <https://aegro.com.br/blog/aegro-fieldview-gestao-fazenda/>
- Agrosmart e Strider: <https://www.b2bstack.com.br/compare/agrosmart-vs-strider>
