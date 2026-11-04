# Revisão de exposição de dados no frontend

Data: 02/11/2026.

## Resultado

Não foram encontradas cópias das credenciais configuradas do backend no código,
nas configurações ou no build do frontend. Foram examinados 394 arquivos de texto,
incluindo os assets públicos e o conteúdo de `web/dist`, excluindo dependências
instaladas e metadados do Git. Valores secretos não foram gravados no relatório.

A revisão encontrou uma consulta pública de perfis e uma regra de alerta somente
no navegador; ambas foram corrigidas. Permanece o risco de armazenamento do token
de sessão em `localStorage`, descrito abaixo. Portanto, este resultado não significa
ausência de todos os riscos de segurança.

## Credenciais e dados públicos

- As credenciais WeatherAPI, Copernicus, Google client secret, JWT, banco e SMTP
  configuradas foram comparadas com os arquivos do frontend sem imprimir valores.
  Nenhuma correspondência foi encontrada. O identificador Copernicus e o usuário
  SMTP também não apareceram nesses arquivos.
- Não foram encontrados tokens JWT fixos, chaves privadas ou credenciais em URLs.
- Não foram encontrados `.env`, certificados, dumps de banco ou logs em `public`
  ou `dist`; também não foram encontrados sourcemaps da aplicação no build.
- Os JSON públicos examinados são dados geográficos do IBGE, sem registros de
  usuários ou polígonos de propriedades.
- O Google OAuth Client ID é uma configuração pública do login. O Google client
  secret permanece no backend; o servidor verifica o token Google e sua audience.
- Mapas e ícones meteorológicos usam recursos públicos. As consultas autenticadas
  à WeatherAPI e ao Copernicus são realizadas pelo backend, sem fornecer suas
  credenciais ao navegador.
- Senhas, códigos e tokens enviados pelo frontend não foram encontrados em
  parâmetros de URL nem em logs próprios do cliente. O token de acesso é enviado
  no cabeçalho Authorization da API, conforme o contrato atual.

## Correções realizadas

| Achado | Correção | Arquivos principais |
| --- | --- | --- |
| Consulta anônima de perfil por UUID retornava nome/foto. Um nome vindo do Google podia ser o próprio e-mail. | `/users/{userId}` exige autenticação e só consulta o próprio usuário. IDs de terceiros retornam 404 antes do acesso ao banco. `/users/count` também exige autenticação. | `SecurityConfig.java`, `UserController.java`, `UserService.java` |
| Dashboard definia sozinho quais leituras contavam como alerta. | Serviços de chuva e nível retornam `requiresAttention`; dashboard apenas filtra e agrega os indicadores recebidos. Chuva forte/muito forte e nível ATENCAO preservam os critérios anteriores. | `RainService.java`, `ReferenceLevelService.java`, DTOs de chuva/nível, `dashboard-page.tsx`, `hydro-data.ts` |
| Mensagens 5xx arbitrárias podiam ser exibidas na página de propriedades. | Respostas 5xx usam a mensagem genérica da operação; tratamento dos erros de domínio 4xx permanece. | `web/src/lib/property-data.ts` |
| Vite expunha automaticamente qualquer variável com prefixo VITE_. | Inclusão automática desativada. Apenas cinco configurações públicas explicitamente permitidas entram no cliente; proxy é configuração exclusiva do processo Vite. | `web/vite.config.ts`, `web/src/types/vite-env.d.ts` |
| Alguns arquivos de ambiente poderiam ser adicionados ao repositório web por acidente. | `.env.*` ignorados, preservando `.env.example`; contexto Docker também exclui `.git`. | `web/.gitignore`, `web/.dockerignore`, `web/.env.example` |

Configurações públicas permitidas no bundle:

- `VITE_API_URL`
- `VITE_GOOGLE_CLIENT_ID`
- `VITE_LEGAL_COMPANY`
- `VITE_LEGAL_CONTACT_EMAIL`
- `VITE_TIME_ZONE`

Esses campos são visíveis ao usuário do navegador. Credenciais de fornecedores
não devem ser colocadas nesses campos.

A coleção Postman, seu gerador e README foram atualizados para que a consulta
por ID e a contagem herdem o Bearer da coleção. O UUID da consulta de perfil deve
ser o do próprio usuário autenticado.

## Risco que permanece: sessão no localStorage

`web/src/providers/auth-provider.tsx:32` e `:41` leem e persistem token, nome,
e-mail e demais dados da própria sessão em `localStorage`. Scripts executados
na mesma origem conseguem acessar esse conteúdo. O token dura sete dias
(`TokenService.java:23`) e o logout comum limpa o contexto/localStorage, sem
invalidar uma cópia anterior do token (`AuthService.java:157`). Logout de todas
as sessões e troca de senha/e-mail usam a revogação existente no backend.

Não foi comprovado roubo de token nem foi localizada injeção de script explorável
nesta revisão. A persistência acessível a JavaScript é, entretanto, uma superfície
real de exposição se ocorrer execução de código malicioso na origem.

A correção completa requer mudança coordenada do contrato de autenticação,
por exemplo sessão em cookie HttpOnly, Secure em HTTPS e SameSite, acompanhada de
proteção CSRF, emissão/expiração/revogação e atualização dos fluxos de login,
Google, logout, recuperação e textos legais. Essa migração não foi realizada nesta
revisão. Mudar apenas para sessionStorage não impede leitura por scripts da origem.

Referências para o armazenamento da sessão:
[OWASP: Local Storage](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html#local-storage)
e [OWASP: Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

## Regras conferidas no servidor

- Propriedades: proprietário obtido do principal autenticado; consulta, edição,
  exclusão, estatística NDVI e imagem verificam o proprietário no backend.
- A verificação de propriedade ocorre antes da recuperação do cache Copernicus.
- Geometria, limites de pontos/extensão, fechamento, interseções, área e centro
  são validados/calculados no backend.
- Período máximo, datas futuras e cobertura de nuvens são validados no backend.
- Cadastro, senha, e-mail, código de verificação e endereço da foto têm validação
  também nos DTOs e serviços do backend.
- Classificação das leituras SNIRH/ANA e indicação de atenção vêm do backend.
- Guardas de rotas React controlam navegação. O Spring Security valida acesso
  à API independentemente de alterações no estado do navegador.

Prévia de área durante desenho, validação imediata de formulário, cores,
formatação, filtros, seleção de propriedades e agregações de visualização
continuam no frontend. Esses comportamentos não concedem autorização nem
substituem a validação do servidor.

## Verificação executada

1. `npm run build`: TypeScript e build de produção concluídos.
2. Build com duas variáveis fictícias fora da lista permitida, incluindo um nome
   iniciado por uma configuração permitida: nenhum valor fictício apareceu no
   bundle. Um segundo build, somente em memória, tentou acessar essas variáveis
   explicitamente e pelo objeto import.meta.env: nenhum nome ou valor não
   permitido foi exposto. A configuração resolvida confirmou zero variáveis
   automaticamente públicas e apenas as cinco definições permitidas.
   Nenhuma credencial real foi usada nesses testes.
3. Varredura do código/configuração/assets/build: zero correspondências das
   credenciais comparadas, zero candidatos pelos padrões examinados e zero
   arquivos privados nos diretórios públicos.
4. Testes locais `UserControllerTest`, `UserServiceTest`, `RainServiceTest`,
   `ReferenceLevelServiceTest` e `HydroControllerTest`: 51 executados,
   zero falhas, zero erros. Os serviços externos foram simulados.
5. Requisições locais sem autenticação a `/users/{userId}`, `/users/count` e
   `/api/properties`: HTTP 401.
6. Requisições ao `.env.local`, arquivos de ambiente via `/@fs/` e `.git/config`
   no Vite: HTTP 403, sem conteúdo privado. `/.env`, inexistente no web,
   respondeu somente o HTML da SPA, sem conteúdo de ambiente.

Alguns testes antigos ainda usavam os nomes em português anteriores dos DTOs.
As referências necessárias foram atualizadas para permitir compilar os testes;
a expectativa de data foi alinhada ao fuso configurado. Nenhum dado de propriedade
ou cadastro real foi criado, alterado ou excluído durante a verificação.

Evidências locais sem valores secretos: `target/runtime/audit-frontend-result.json`,
`target/runtime/frontend-env-policy-audit.json`, `target/runtime/frontend-http-audit.json`
e relatórios em `target/surefire-reports`.

## Limites

Esta é uma revisão do estado atual do workspace, dos artefatos gerados e dos
fluxos locais descritos. Ela não inspeciona histórico de Git, backups, configurações
de servidores de produção, extensões do navegador ou vulnerabilidades de todas
as dependências. Não constitui comprovação de que nunca houve exposição em outra
versão ou ambiente.
