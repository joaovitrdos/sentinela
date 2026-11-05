# Sentinela

O Sentinela é uma aplicação web para acompanhar propriedades rurais e organizar o trabalho de campo. O usuário delimita propriedades, cadastra talhões e safras, registra operações e fotos, atribui ações à equipe e consulta informações ambientais e relatórios.

O foco do produto é o histórico operacional por propriedade, talhão e safra. Dados de clima, hidrologia e satélite apoiam a observação; não substituem a verificação local nem um diagnóstico agronômico.

## Funcionalidades

- Propriedades e talhões com localização e delimitação no mapa.
- Safras por talhão, com cultura, variedade, plantio e colheita.
- Caderno de campo com operações, insumos, quantidades, responsável e fotos.
- Ações com responsável, prazo, lembrete e registro de conclusão.
- Equipes por propriedade com papéis de visualizador, editor e administrador.
- Consultas de clima e previsão pela WeatherAPI, chuva e hidrologia pelo SNIRH/ANA e monitoramento por imagens Copernicus quando configurado.
- Relatórios JasperReports em PDF, HTML, XLSX, ODT, CSV, RTF e TXT.

## Tecnologia e estrutura

| Camada | Tecnologia | Local |
| --- | --- | --- |
| API | Java 21, Spring Boot 4.1.1, Spring Security, JPA | `src/main/java` |
| Banco | PostgreSQL 17, migrações Flyway | `src/main/resources/db/migration` |
| Relatórios | JasperReports 7.0.8 e modelos JRXML | `src/main/resources/reports` |
| Interface | React 19, TypeScript 6, Vite 8, Tailwind CSS | `web` |
| Implantação | Docker Compose; Caddy e Nginx no perfil de produção | `docker-compose*.yaml` |

O `ReportService` executa os relatórios. Cada `ReportProvider` declara um código permitido, valida parâmetros e prepara dados de JDBC ou de uma integração externa. O cliente não envia consultas SQL nem caminhos de arquivos JRXML.

## Requisitos

- **Com Docker:** Docker Compose 2.24 ou superior.
- **Sem Docker:** JDK 21, PostgreSQL 17, Node.js compatível com Vite 8 e npm.

## Início rápido com Docker

1. Copie `.env.example` para `.env`.
2. Defina, no mínimo, `DB_PASSWORD` e `SENTINELA_JWT_SECRET` no `.env`. O segredo JWT deve ter pelo menos 32 caracteres.
3. Configure `WEATHER_API_KEY` para as consultas e relatórios WeatherAPI. SMTP, Google e Copernicus dependem das respectivas variáveis no `.env.example`.
4. Inicie os serviços:

   ```sh
   docker compose up -d --build
   ```

5. Acesse `http://localhost` (ou a porta definida em `WEB_PORT`). A API usa `http://localhost:8080` por padrão e o PostgreSQL fica restrito ao loopback do host.

As migrações Flyway são aplicadas ao iniciar a API. Para uso fora de desenvolvimento, defina `JPA_DDL_AUTO=validate` e use as migrações para evoluir o banco.

## Execução local sem Docker

Com PostgreSQL em execução e `.env` configurado na raiz:

```sh
./mvnw spring-boot:run
```

No Windows, use `mvnw.cmd spring-boot:run`. Confirme que o Maven está usando **Java 21** (`mvn -v`); uma instalação de Java 8 no `JAVA_HOME` impede a compilação.

Em outro terminal:

```sh
cd web
cp .env.example .env.local
npm ci
npm run dev
```

No Windows, use `Copy-Item .env.example .env.local`. O frontend local fica em `http://localhost:5173`; o Vite encaminha `/api` para `http://localhost:8080`. Chaves privadas permanecem no `.env` da API, nunca no diretório `web`.

## Swagger / OpenAPI

Com a API iniciada, abra `http://localhost:8080/swagger-ui.html`. A especificação JSON fica em `http://localhost:8080/v3/api-docs`.

O botão **Authorize** aceita o JWT de uma conta autenticada para chamar endpoints protegidos. O Swagger está habilitado por padrão no ambiente local; `SPRINGDOC_ENABLED=false` desativa a interface e a especificação. O Compose de produção já aplica esse valor.

## Relatórios disponíveis

Na tela **Relatórios**, o usuário escolhe um relatório, os filtros aplicáveis e o formato do arquivo.

| Origem | Relatórios |
| --- | --- |
| Sentinela | Propriedades e atividades; resumo de atividades por propriedade; resumo mensal de irrigação; histórico de campo por safra |
| SNIRH | Chuva acumulada em 24 horas por estação; situação dos níveis hidrológicos |
| WeatherAPI | Condições atuais; previsão diária de 1 a 3 dias; fuso horário da propriedade |

Os relatórios WeatherAPI consultam a fonte no momento da geração e exigem uma propriedade à qual o usuário tenha acesso. O período disponível para previsão também depende do plano e da disponibilidade do provedor. Consulte [docs/reports.md](docs/reports.md) para os códigos, modelos e regras de parâmetros.

## Testes e build

```sh
./mvnw test
cd web
npm run build
```

## Produção e documentação

- [Implantação com HTTPS](docs/deploy-producao.md)
- [Relatórios Jasper](docs/reports.md)
- [Recursos rurais e limites dos dados](docs/rural-features.md)
- [Monitoramento Copernicus](docs/copernicus-properties.md)
- [Disponibilidade e manutenção](docs/system-availability.md)
- [Visão do projeto em PDF](output/pdf/sentinela-visao-do-projeto.pdf)
- [Fonte editável do PDF](docs/sentinela-visao-do-projeto.html)

Para produção, use `docker-compose.yaml` com `docker-compose.prod.yaml`, configure domínio e HTTPS conforme o guia e mantenha segredos fora do controle de versão.
