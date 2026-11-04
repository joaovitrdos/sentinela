# Monitoramento de propriedades com Copernicus

## O que está integrado

A página **Propriedades** fica entre **Início** e **Dashboard**. Cada conta cadastra
suas áreas, desenha os limites no mapa e consulta o NDVI do Sentinel-2 L2A para
o polígono salvo. A página principal de monitoramento e o dashboard continuam
separados.

O cadastro funciona sem uma conta Copernicus configurada. Para buscar imagens
e observações reais, configure as duas credenciais abaixo no backend.

## 1. Criar a conta Copernicus

1. Acesse o [Copernicus Data Space Ecosystem](https://dataspace.copernicus.eu/)
   e crie uma conta, caso ainda não tenha uma.
2. Conclua o cadastro e entre na conta.
3. No menu do perfil, abra **Sentinel Hub** para acessar o painel do serviço.

O [guia oficial de início](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/UserGuides/BeginnersGuide.html)
mostra esse acesso.

## 2. Criar as credenciais do backend

1. No painel Sentinel Hub, abra **User Settings**.
2. Na seção **OAuth clients**, clique em **Create**.
3. Informe um nome, por exemplo `Sentinela Backend`, e escolha uma validade.
4. Deixe a opção de aplicação SPA desmarcada: a integração é feita pelo servidor.
5. Conclua a criação e guarde o **Client ID** e o **Client Secret**. Copie o
   secret nessa ocasião, pois ele não fica disponível para consulta posterior.

As etapas e o endpoint de autenticação estão na
[documentação oficial de OAuth](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Overview/Authentication.html).

## 3. Preencher o `.env` do Spring Boot

Na raiz do projeto, abra o arquivo `.env`, junto do `pom.xml`. Acrescente ou
preencha:

```dotenv
COPERNICUS_CLIENT_ID=seu_client_id
COPERNICUS_CLIENT_SECRET=seu_client_secret
```

Use os valores do cliente OAuth criado no passo anterior. Essas variáveis
ficam apenas no backend; não as coloque em `web/.env.local`, em variáveis
`VITE_`, no navegador nem no código versionado. O `.env.example` contém
somente os nomes das variáveis, sem credenciais.

O `application.yaml` já aponta para os endpoints do **Copernicus Data Space**:

| Finalidade | Endpoint externo |
| --- | --- |
| Token OAuth | `https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token` |
| Imagem NDVI | `https://sh.dataspace.copernicus.eu/process/v1` |
| Série de NDVI | `https://sh.dataspace.copernicus.eu/statistics/v1` |

O servidor solicita o token com `client_credentials`, reutiliza-o enquanto
válido e renova a autenticação quando necessário. O usuário do Sentinela
continua entrando com sua própria conta do sistema.

## 4. Reiniciar as aplicações

Depois de preencher as credenciais, reinicie o backend para reler o `.env`.
Com Java 21 configurado, na raiz do projeto:

```powershell
mvn spring-boot:run
```

Se o Maven não estiver no `PATH`, utilize o caminho da instalação local.
Em outro terminal, inicie o frontend:

```powershell
Set-Location web
npm run dev
```

No ambiente local atual, o frontend usa `http://localhost:5173` e encaminha
as chamadas ao backend por meio do proxy do Vite. O destino do proxy fica em
`web/.env.local`, em `VITE_API_PROXY_TARGET`; ele deve coincidir com a porta
`SERVER_PORT` do backend. Não é necessário mudar as URLs das APIs no frontend.

### Banco de dados

O cadastro usa a entidade existente `Property` e a tabela `tb_property`.
Foram acrescentados `polygon_json` e `area_hectares`, mantendo os campos
anteriores. Os cadastros antigos podem ser abertos para desenhar o contorno.

Em desenvolvimento, `JPA_DDL_AUTO=update` aplica as novas colunas. Se o ambiente
usa `validate`, aplique antes uma migração equivalente no PostgreSQL:

```sql
ALTER TABLE tb_property ADD COLUMN IF NOT EXISTS polygon_json text;
ALTER TABLE tb_property ADD COLUMN IF NOT EXISTS area_hectares double precision;
```

## 5. Cadastrar uma propriedade

1. Entre no Sentinela e abra **Propriedades**.
2. Crie uma propriedade e informe nome, cidade e estado.
3. Localize a área pela busca de cidade, pela localização atual ou navegando
   no mapa. A localização atual depende da permissão do navegador e usa HTTPS
   ou `localhost`.
4. Inicie o desenho e clique nos vértices do contorno. Conclua com pelo menos
   três pontos. Use desfazer/reiniciar ou ajuste os vértices para corrigir.
5. Salve. A área aproximada em hectares é calculada pelo servidor.
6. Selecione a propriedade salva e consulte o período desejado no painel de
   monitoramento.

O mapa ocupa a área disponível da página, no mesmo padrão do Início. O botão
**Filtrar propriedades**, junto da busca de cidade, permite escolher uma ou
mais áreas, selecionar todas ou limpar a seleção. O mapa enquadra os contornos
selecionados. Clique em uma área para abrir suas informações e consultar seu
NDVI; ao selecionar várias, a consulta de satélite continua vinculada à área
em foco, identificada no painel.

O cadastro e a edição ficam em um painel flutuante recolhível. Para excluir um
cadastro, abra **Configurações → Propriedades**, selecione a área e confirme no
diálogo de exclusão.

A área desenhada é um cadastro de monitoramento feito pelo usuário, não uma
certificação de limites cadastrais. O acesso no sistema é vinculado à conta
que criou a propriedade.

## 6. Consultar e interpretar os dados

- Escolha um período de até 90 dias, sem datas futuras, e o limite de nuvens.
- O painel apresenta as observações válidas retornadas pela API, a média de
  NDVI e a diferença entre as duas últimas observações válidas do período,
  quando houver pelo menos duas.
- Selecione uma observação para exibir sua imagem de NDVI sobre o mapa.
- Use **Ocultar NDVI / Mostrar NDVI** para alternar entre o índice e o mapa
  base sem fazer outra consulta à API.
- A consulta ocorre ao selecionar a propriedade e mudar os filtros. Enquanto
  a página está visível, ela também solicita atualização após uma hora; ao
  voltar à aba, verifica se esse intervalo já passou. O servidor reutiliza
  resultados por até seis horas para reduzir o consumo da cota.
- Os atalhos **Últimos 7/30/90 dias** avançam com a data atual durante as
  atualizações. Datas personalizadas continuam fixas para comparação histórica.
- Não há varredura das propriedades em segundo plano com o navegador fechado
  nem envio de alertas por este módulo.
- As datas das observações de satélite são apresentadas como dias UTC; elas
  não indicam uma leitura instantânea do momento da consulta.

O índice é calculado por `(B08 - B04) / (B08 + B04)`. As bandas têm resolução
nativa de 10 metros. A classificação de cena SCL é usada para descartar pixels
inadequados, incluindo nuvens, sombras e neve. O limite de nuvens da busca é
um filtro sobre a cena, não a porcentagem de nuvens exata da propriedade.
[Bandas e filtros Sentinel-2 L2A](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Data/S2L2A.html).

Valores mais altos de NDVI costumam indicar maior presença de vegetação verde.
Solo exposto, água, época do ano e tipo de cultura alteram a interpretação.
As cores ajudam a comparar o índice; não são um diagnóstico agronômico nem
uma classificação universal de risco. **NDVI não mede sozinho temperatura,
chuva, umidade do solo ou focos de incêndio.** Os números da imagem de exemplo
não foram usados como medições no sistema.

O percentual **Pixels úteis** divide a quantidade de pixels com NDVI válido,
depois das máscaras, pelo total de pixels que intersectam o contorno salvo
(`geometryPixelCount` retornado pelo Copernicus). Assim, nuvens e ausência de
imagem reduzem a proporção útil da propriedade. Quando o total de pixels do
contorno não é informado ou não é positivo, o percentual fica indisponível
e não é exibido como uma porcentagem. Essa contagem de pixels não representa
uma medição exata da porcentagem de hectares cobertos. Áreas sem imagem não
são convertidas em NDVI zero. A resolução efetiva de amostragem é informada
pelo retorno; para limitar processamento, a imagem tem no máximo 512 pixels
no maior lado.
[Contagem oficial de pixels da geometria](https://docs.sentinel-hub.com/api/latest/api/statistical/#geometry-pixel-count).

## Como a área e o acesso são restringidos

- O backend identifica o proprietário pela sessão autenticada; não recebe
  um `userId` escolhido pelo navegador.
- Listagem, leitura, edição, exclusão, série NDVI e imagem verificam o vínculo
  com esse usuário. Um identificador de outra conta retorna 404.
- O servidor usa o polígono persistido na propriedade para todas as consultas
  de satélite. Alterar parâmetros da URL não permite enviar uma área diferente.
- Os pedidos ao Copernicus incluem a geometria completa. A máscara `dataMask`
  exclui os pixels externos; o PNG os deixa transparentes e as estatísticas
  não os incluem nas médias. O retângulo delimitador serve para posicionar a
  imagem, não para ampliar a área monitorada.
  [Máscara oficial de dados](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/UserGuides/Datamask.html).
- A geometria é GeoJSON `Polygon` em WGS84, com coordenadas na ordem
  `[longitude, latitude]`. Esta versão aceita um contorno simples, sem buracos,
  com até 200 vértices, até 10.000 hectares e extensão limitada para controlar
  o custo das consultas. Contornos que se cruzam são rejeitados.
- O cache inclui a propriedade e sua geometria; uma alteração do contorno
  produz outra consulta. Mesmo os resultados em cache exigem acesso autorizado.

## APIs internas

Todas exigem o token do usuário do Sentinela em `Authorization: Bearer ...`.
As rotas abaixo são as do **backend direto**, sem o prefixo adicional do
proxy do frontend.

| Método | Rota | Uso |
| --- | --- | --- |
| GET | `/api/properties` | Listar as propriedades da conta |
| POST | `/api/properties` | Cadastrar uma área |
| GET | `/api/properties/{propertyId}` | Ler uma propriedade da conta |
| PUT | `/api/properties/{propertyId}` | Atualizar dados e contorno |
| DELETE | `/api/properties/{propertyId}` | Excluir uma propriedade |
| GET | `/api/properties/{propertyId}/monitoring?from=2026-10-01&to=2026-10-30&maxCloudCoverage=40` | Consultar a série real do período |
| GET | `/api/properties/{propertyId}/ndvi.png?date=2026-10-20&maxCloudCoverage=40` | Gerar imagem do dia escolhido |

Exemplo de corpo de cadastro/edição; substitua o contorno pela área desejada:

```json
{
  "name": "Área de cultivo",
  "city": "Colatina",
  "state": "ES",
  "geometry": {
    "type": "Polygon",
    "coordinates": [[
      [-40.6500, -19.5400],
      [-40.6470, -19.5400],
      [-40.6470, -19.5430],
      [-40.6500, -19.5430],
      [-40.6500, -19.5400]
    ]]
  }
}
```

O último ponto repete o primeiro para fechar o anel. O backend calcula o
centro e a área; não é necessário enviá-los.

## Cota e resolução de problemas

A conta Copernicus configurada é do serviço: sua cota é compartilhada pelos
usuários deste backend. Consulte o consumo no painel Sentinel Hub. Na
documentação consultada, usuários gerais têm 10.000 requisições e 10.000 PU
por mês; o custo em PU varia com o processamento, não é uma relação de uma
unidade por propriedade. Confirme as condições atuais da conta em
[Cotas e limitações](https://documentation.dataspace.copernicus.eu/Quotas.html).

| Situação | Como resolver |
| --- | --- |
| Integração não configurada | Preencha as duas variáveis no `.env` e reinicie o backend. |
| Contorno pendente | Edite o cadastro antigo, desenhe o polígono e salve. |
| Nenhuma observação válida | Amplie o período ou ajuste o limite de nuvens. Pode não existir passagem útil. |
| Imagem parcialmente transparente | Há nuvens, sombras ou ausência de dados; os pixels descartados não são inventados. |
| Falha de autenticação Copernicus | Confira o cliente OAuth, sua validade e o secret no backend. |
| HTTP 429 | A cota ou o limite de chamadas foi atingido. Consulte o painel e aguarde a liberação. |
| HTTP 502/503 da integração | Serviço externo indisponível ou credenciais inválidas; o cadastro da propriedade continua independente. |
| HTTP 404 de uma propriedade | O cadastro não existe ou não pertence à conta autenticada. |

Referências técnicas:
[Processing API](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Process.html)
e [Statistical API](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Statistical.html).
