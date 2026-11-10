# PluvIFFar — API (Spring Boot)

> Itens marcados com `TODO` ainda precisam ser preenchidos. Se um TODO bloquear a tarefa, pergunte em vez de supor.

## Idioma (regra do projeto)

- **Inglês** em tudo que é código e contrato: schema do banco (schemas, tabelas, colunas, constraints), classes, pacotes, métodos, variáveis, endpoints, JSON da API, enums/roles e **campos do payload MQTT** (próxima versão do protocolo).
- **pt-BR** apenas em: mensagens ao usuário (`messages.properties`), comentários no código/SQL, mensagens de commit, documentação e conversa comigo.
- Ex.: tabela `readings` com coluna `precipitation_mm`, comentário `-- chuva no intervalo`; erro `ErrorCode.STATION_NOT_FOUND` → mensagem "Estação não encontrada".

## O projeto

O PluvIFFar é um sistema de monitoramento pluviométrico para agricultores. É um produto único, dividido em 4 TCCs:

| TCC | Escopo | Responsável |
|---|---|---|
| 1 | Dispositivo IoT / aquisição (pluviômetro + gateway) | TODO |
| 2 | Plataforma web / UX | TODO |
| **3** | **Backend e arquitetura (este repo)** | **Eduardo**, orientação do Prof. Dr. Everton Lutz |
| 4 | Análise de dados e predição | TODO |

Este repositório é a **API de gerenciamento** do PluvIFFar: autenticação/autorização, cadastro de entidades (usuários, gateways, pluviômetros...) e leitura dos dados de chuva já gravados no banco. Depois dela virá o frontend da plataforma.

Cronograma do TCC 3: maio a novembro de 2026. TODO: datas de entrega/defesa.

## Arquitetura geral

```
Pluviômetro ──ESP-NOW──▶ Gateway (rede doméstica do agricultor)
                              │ MQTT
                              ▼
                 Mosquitto (VPS) ──▶ Worker (assina pluviffar/#) ──▶ PostgreSQL + TimescaleDB
                                                                          ▲
                                              API Spring (este repo) ─────┘
                                                      ▲
                                                Frontend (TODO)
```

- **Um único banco** PostgreSQL serve a aplicação e o data warehouse (decisão de arquitetura: Postgres + TimescaleDB em vez de InfluxDB).
- O **worker** é quem consome o MQTT e grava os dados brutos. A API **não** assina o broker. TODO: confirmar onde o worker vive (repo separado? linguagem?).
- TODO: a API vai **publicar** em `pluviffar/{gateway_id}/conf` (ex.: alterar `tempo_envio`)? Ou config é só leitura?

## Hardware (TCC 1, para contexto)

- Pluviômetro: ESP8266 com sensor hall (contagem de "tips" da báscula).
- Gateway interno: ESP8266 conectado ao Wi-Fi do produtor; recebe do pluviômetro via ESP-NOW e publica no broker.

## Infraestrutura (VPS)

- VPS privada: 2 vCPU, 8 GB RAM, 100 GB NVMe, 8 TB de banda. IPv4 `72.60.10.6`, domínio `pluviffar.com.br`.
- Ubuntu Server 26.04. Usuário SSH `pluviffar`.
- **Apache** instalado servindo um `index.html` do projeto em `/var/www/html` (porta 80). Se a API ficar atrás de proxy reverso, o caminho natural é o próprio Apache (`mod_proxy`) — TODO: confirmar.
- **Node.js v22.22.1** + npm instalados via apt (relevante se o frontend for Next.js).
- **Mosquitto** (instalado via apt):
    - `/etc/mosquitto/conf.d/pluviometro.conf`: `listener 1883 0.0.0.0`, `allow_anonymous false`, `password_file /etc/mosquitto/passwd`.
    - `/etc/mosquitto/passwd` com `root:mosquitto`, `640`.
    - ACL em `/etc/mosquitto/acl`: `user pluviometro` → `topic readwrite pluviffar/#`. Tópicos fora disso são recusados (`not authorised`) — testado.
    - Usuário MQTT `pluviometro`. Porta 1883 aberta, **sem TLS** (TODO: avaliar 8883/TLS).
    - Tópicos antigos de teste (`pluviometro/teste`, `pluvio/dados`) não fazem parte do protocolo; ignorar.
- **PostgreSQL 18** (instalado via apt, `postgresql-contrib`) na mesma VPS: banco `pluviffar`, role/dono `pluviffar` (criados via `sudo -u postgres psql`).
- TimescaleDB 2.25.1, **edição Apache** → **sem compressão** e sem recursos da licença TSL. Não usar `add_compression_policy`, continuous aggregates com recursos TSL etc. sem checar.
- O time mantém o "Relatório de Bordo PluvIFFar 2026" com instalações e configurações do servidor.
- **Credenciais nunca neste arquivo nem no repo**: ficam em variáveis de ambiente / `.env` / `CLAUDE.local.md` (gitignored). Na API: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, senha da seed do ADMIN etc. via env (ver `.env.example`).
- TODO: como a API será deployada (jar + systemd? Docker? proxy reverso nginx/caddy + TLS?).
- **Ambiente de dev: Docker** (`compose.yaml` na raiz): TimescaleDB com PostgreSQL 18 na edição Apache (igual à produção). Banco, usuário e senha `pluviffar`. Em dev o usuário é superusuário; na VPS **não** é — migrations não devem depender de criar extensões (a `timescaledb` já existe no banco).

## Protocolo MQTT PluvIFFar v1.0 (out/2026)

Tópicos: `pluviffar/{gateway_id}/{dados|conf|status}` — ex.: `pluviffar/gt-570db4/dados`.
O campo `id` no payload é o **id do pluviômetro** (ex.: `580db2`), não do gateway.

| Tópico | Campos do payload |
|---|---|
| `dados` | `id`, `tips_por_minuto`, `mm`, `timestamp`, `seq` |
| `conf` | `id`, `vers_gt`, `vers_pl`, `hw_gt`, `hw_pl`, `tempo_envio`, `mm_tips` |
| `status` | `id`, `vbat`, `bat`, `rssi_gt`, `rssi_pluv`, `uptime_gt_s`, `uptime_pluv_s`, `timestamp` |

Especificação completa: documento "Protocolo MQTT para Transmissão de Dados — PluvIFFAR v1.0" (Google Docs do time).

Exemplos reais de payload:

```json
// pluviffar/gt-570db4/dados
{ "id": "580db2", "tips_por_minuto": 4, "mm": 0.8, "timestamp": 1790550000, "seq": 1 }

// pluviffar/gt-570db4/conf
{ "id": "580db2", "vers_gt": "1.0.0", "vers_pl": "1.0.0", "hw_gt": "1.0", "hw_pl": "1.0",
  "tempo_envio": 60, "mm_tips": 0.20 }

// pluviffar/gt-570db4/status
{ "id": "580db2", "vbat": 6.5, "bat": 74, "rssi_gt": -86, "rssi_pluv": -60,
  "uptime_gt_s": 86432, "uptime_pluv_s": 85321, "timestamp": 1790550000 }
```

Tipos e unidades (inferidos dos exemplos — TODO: confirmar com o TCC 1):

- `timestamp`: **epoch Unix em segundos (UTC)** → mapear para `Instant` / `timestamptz`.
- `id` (pluviômetro) e `gateway_id`: strings hex curtas (`580db2`, `gt-570db4`).
- `mm`, `mm_tips`: decimal em milímetros (`mm_tips` = mm por basculada, ex. 0.20).
- `tips_por_minuto`, `seq`: inteiros. `seq` serve para detectar perda/duplicidade.
- `vbat`: volts (decimal); `bat`: percentual (0–100); `rssi_*`: dBm (inteiro negativo).
- `uptime_*_s`, `tempo_envio`: segundos (inteiro).
- `vers_*`, `hw_*`: strings de versão.

### ⚠️ Divergências a resolver

1. **O protocolo v1.0 acima é o que está em vigor** (confirmado). O **schema legado** (`docs/legacy-schema.sql`) foi feito para um payload antigo (`pluvio/dados` com `t`, `tips`, `total`, `lq` e bateria junto) e está **desatualizado** — o redesign deve seguir o v1.0 (`dados`/`conf`/`status`).
2. Pela regra de idioma, os campos do payload vão para **inglês** numa próxima versão do protocolo (ex.: `tips_per_minute`, `fw_gateway`...). Mudanças no **firmware** e no **worker** ficam para depois e serão feitas pelo Eduardo — aqui, só propor o mapeamento PT→EN (pode virar `docs/mqtt-protocol-v2.md`). Não assumir que já mudou: enquanto isso, o worker continua recebendo o v1.0 em português.

## Banco de dados

### Schema atual (redesign aplicado — V1/V2)

**Ver `docs/schema.md`** (decisões, hypertables, mapeamento do protocolo, mudanças no worker) e `docs/mqtt-protocol-v2.md` (proposta PT→EN). Resumo:

- `app`: `users`, `gateways`, `stations` (PK `uuidv7()`, chip em `hardware_id` único entre não excluídos, **soft delete** via `deleted_at`, FK composta garante dono da estação = dono do gateway), `spring_session*`, `flyway_schema_history`.
- `telemetry`: `readings` e `device_status` (hypertables, chunk 30 dias, PK `(station_id, measured_at)` + `ON CONFLICT DO NOTHING`), `config_reports` (tabela comum, histórico do `conf`).
- Seed do ADMIN: `db.migration.V2__SeedAdmin` (Java), lê `PLUVIFFAR_ADMIN_EMAIL`/`PLUVIFFAR_ADMIN_PASSWORD` via placeholders do Flyway.
- **As migrations vivem neste repo**; o worker só lê/escreve.

### Legado (referência)

`docs/legacy-schema.sql` — TODO: o arquivo está vazio; colar o DDL legado se for preciso migrar dados de `dados.*`.

Resumo: schema `dados` com `estacoes` (PK = chip ID em hex, auto-cadastrada pelo worker no primeiro pacote) e `leituras` (hypertable por `medido_em`, chunks de 30 dias, PK `(estacao_id, medido_em)` para deduplicar). Bloco de compressão condicional à licença (pulado na edição Apache). **Não há** usuários, gateways, dono, visibilidade pública, status nem conf.

### Requisitos do redesign (✅ atendidos pela V1 — manter ao evoluir o schema)

- **Domínio:**
    - `station` = **pluviômetro** (sensor). Identificado pelo chip ID do payload (`id`).
    - `gateway` = ESP8266 que recebe dos pluviômetros e publica no broker (`gateway_id` do tópico). Um gateway tem N estações.
    - `user` com role `ADMIN` ou `PRODUCER`.
- **Posse:** gateways e estações pertencem a um produtor. O `PRODUCER` faz CRUD completo dos **próprios** gateways e estações.
- **Visibilidade pública:** cada estação tem um flag definido pelo dono (ex.: `is_public`). A área pública (sem login) mostra **apenas** dados de estações públicas, sem expor dados do dono.
- **Cadastro só pela plataforma (decidido):** gateways e estações existem no sistema **apenas** se cadastrados via API (pelo produtor ou admin), informando o chip ID. **Fim do auto-cadastro pelo worker.** Só leituras/status de equipamentos cadastrados são computados; o worker vai **ignorar** pacotes de equipamentos desconhecidos. O ajuste no worker fica para depois (não implementar aqui) — mas o schema deve suportar isso: `owner_id` obrigatório, FKs das hypertables para estações cadastradas, chip ID único. Esse comportamento pode ser revisto no futuro.
- **Telemetria (hypertables):**
    - leituras de chuva (tópico `dados`), com deduplicação (PK com tempo; considerar `seq`);
    - status do dispositivo (tópico `status`: bateria, RSSI, uptime) — hypertable separada;
    - configuração (tópico `conf`: versões de fw/hw, `send_interval`, `mm_per_tip`) — estado atual na estação/gateway e/ou histórico.
- **Separar** tabelas da aplicação e de telemetria (ex.: schemas `app` e `telemetry` — sugestão, decidir).
- Manter o bloco de compressão **condicional à licença** do Timescale (produção é edição Apache). Sem retenção: dado medido não se apaga.
- Avaliar `real` vs `numeric`/`double precision` para mm, e índices para consultas por estação + período.
- Atualizar o **worker** para o novo schema é consequência — listar o que muda para ele.

### Migrations e regras gerais

- O redesign inclui também a tabela de **usuários** e as tabelas do **Spring Session** (ver "Sessão server-side").
- Usar **Flyway**; o redesign vira `V1__...`. Seed do usuário **ADMIN** via migration/seed (senha via variável de ambiente ou hash gerado fora do repo — nunca senha em texto no SQL commitado).
- Worker e API compartilham o banco: **as migrations vivem neste repo** e o worker só lê/escreve (decidido).
- Regras:
    - `spring.jpa.hibernate.ddl-auto=validate` (ou `none`). **Nunca** `update`/`create` — o schema é controlado por migrations.
    - Hypertables não têm PK simples por id; mapear com chave composta incluindo a coluna de tempo, ou tratar como somente leitura via queries nativas/projeções.
    - Consultas de série temporal (agregação por hora/dia, `time_bucket`) via query nativa ou JdbcTemplate, não JPQL.

## Stack da API

- **Spring Boot 4.1.2**, **Java 21**, **Maven** (wrapper `./mvnw`).
- `groupId` `br.com.pluviffar`, `artifactId` `api` → pacote base **`br.com.pluviffar.api`**.
- Dependências (`pom.xml`; o Boot 4 modularizou os starters — conferir nomes/versões no BOM antes de adicionar, não chutar versão):
    - `spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `-security`, `-flyway` + `flyway-database-postgresql`, `-session-jdbc` (**sem JWT**), driver `org.postgresql:postgresql` (runtime), Lombok;
    - `springdoc-openapi-starter-webmvc-ui` **3.2.0** (linha 3.x = Boot 4; única versão fixada à mão, em `<springdoc.version>`);
    - teste: `-webmvc-test`, `-data-jpa-test`, `-security-test`, `spring-boot-testcontainers` + `testcontainers-postgresql`/`-junit-jupiter`.
- Atenção às APIs novas: **Jackson 3** (`tools.jackson.*`; `JsonNode.asString()`), Spring Security 7 (`DaoAuthenticationProvider(UserDetailsService)`), Spring Data 4 (`org.springframework.data.core.PropertyReferenceException`, `Specification.allOf`), Hibernate 7 (`@UuidGenerator(style = VERSION_7)`), Testcontainers 2 (`org.testcontainers.postgresql.PostgreSQLContainer`).
- Java 21: preferir **records** para DTOs. Em entidades JPA, se usar Lombok, evitar `@Data` (equals/hashCode/toString com relações); usar `@Getter`/`@Setter`.
- Aplicação **somente web** por enquanto (sem app mobile) — o desenho de auth assume navegador.

## Convenções de código (padrões herdados do meu projeto gestaovagas — ajustar se quiser)

- Organização **por módulo** em inglês (ex.: `user`, `auth`, `gateway`, `station`, `telemetry`, `publicdata`), cada um com `entities`, `repositories`, `dtos`, `controllers` e `useCases` (um use case por ação, ex. `CreateUserUseCase`). Transversais em `shared` (`exceptions`, `web` — `PageResponse`, `QuerySupport` para busca LIKE e whitelist de `sort` —, `config`); segurança em `auth.config`/`auth.security`.
- Entidades atuais: `user.entities.User`/`Role`, `gateway.entities.Gateway`, `station.entities.Station` (`assignGateway()` mantém `owner` = dono do gateway), e somente leitura (`@Immutable`) `telemetry.entities.Reading`/`DeviceStatus` (chave `TelemetryId` = station_id + measured_at) e `ConfigReport`. Soft delete: filtrar `deletedAt IS NULL` nas consultas.
- Lombok onde reduzir boilerplate; Bean Validation (`jakarta.validation`) nos DTOs de entrada.
- Mensagens ao usuário em **pt-BR** via `MessageSource` (`messages.properties`).
- Tratamento de erros (`shared.exceptions`) — **implementado**:
    - `ApiErrorDTO` (`code`, `message`, `errors`, `traceId`) + `FieldErrorDTO`. `code` = nome do `ErrorCode` (contrato estável para o front).
    - Enum `ErrorCode` (messageKey + `HttpStatus`) e `BusinessException(ErrorCode, args...)`. Erro novo = constante no enum + chave em `messages.properties`.
    - `GlobalExceptionHandler` (`@RestControllerAdvice` simples): validação (400 com `errors` por campo), JSON malformado, parâmetro inválido (UUID, sort), 404 de rota, 405, 406, 415, `AccessDeniedException` (403, senão viraria 500), integridade (409) e genérico (500, com log).
    - Erros dos filtros do Security (401/403/CSRF) saem pelo `ApiErrorWriter` (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`).
    - `TraceIdFilter`: traceId por requisição no MDC (logs), header `X-Trace-Id` e `ApiErrorDTO`.
    - Mensagens de validação: `message = "{chave}"` nos DTOs, texto em `messages.properties`; locale fixo pt-BR.
- **Uma entidade de usuário única**; diferenciação de perfis por **autorização** (roles), não por fluxos de login separados.
- Login com mensagem única `INVALID_CREDENTIALS` para email inexistente, senha errada **ou usuário desativado** (não revelar se o usuário existe).
- Segredos via propriedades externas / variáveis de ambiente, nunca no código.
- Nunca expor entidades JPA direto nos controllers — sempre DTOs.

## Autenticação e autorização

Dois perfis (roles):

| Role | Pode |
|---|---|
| `ADMIN` | **Superusuário**: cadastra e gerencia usuários e pode ver/editar qualquer gateway, estação e dado. Não há auto-cadastro. O primeiro ADMIN é criado por **seed no banco**. |
| `PRODUCER` | CRUD completo dos **próprios** gateways e estações, e acesso aos dados deles. Define se cada estação é pública. |

- Regra central: todo endpoint de gateway/estação/leitura filtra por dono quando o usuário é `PRODUCER`; `ADMIN` passa direto. Checar posse no backend (use case ou `@PreAuthorize` com verificação de ownership) — nunca confiar em id vindo do cliente. Recurso de outro produtor → 404 (não revelar existência).
- **Como está implementado (seguir o padrão nos próximos módulos, ex. leituras):** `CurrentUser.ownerScope()` devolve o ID do usuário (PRODUCER) ou `null` (ADMIN); os repositórios expõem `visible(ownerScope, includeDeleted)` (Specification) e toda busca passa por ela. `Find*UseCase.getActiveOrThrow(id)` é o ponto único de "buscar com posse ou 404" — reutilize-o (ex.: criar estação valida o gateway por ele).

### Gateways e estações (`/api/gateways`, `/api/stations`) — implementado

- CRUD completo (POST 201 + Location, GET paginado, GET/{id}, PUT/{id}, DELETE/{id} = soft delete) para PRODUCER (só os próprios) e ADMIN (todos).
- Dono: PRODUCER é sempre dono do que cadastra (`ownerId` de outro → 403). ADMIN informa `ownerId` de um PRODUCER ativo (`OWNER_REQUIRED`/`INVALID_OWNER`) e pode **transferir** o gateway no PUT — o banco propaga o dono para as estações (FK composta). A estação herda o dono do gateway (`Station.assignGateway`).
- `hardwareId` (chip ID): normalizado para minúsculas, padrão `^[a-z0-9-]{1,32}$`, **imutável** e único entre os não excluídos (409 `HARDWARE_ID_ALREADY_REGISTERED`, mesmo entre produtores diferentes: é o mesmo equipamento físico).
- Exclusão: lógica (`deleted_at`); libera o chip para novo cadastro. Gateway com estação ativa não pode ser excluído (409 `GATEWAY_HAS_ACTIVE_STATIONS`). Estação excluída tem `is_public` desligado.
- ADMIN: filtros `ownerId` e `includeDeleted` nas listagens e vê excluídos no detalhe (mas não altera). Para PRODUCER esses filtros são ignorados.
- Estação: latitude/longitude juntas ou nenhuma (`locationComplete`); pública exige localização (`publicWithLocation`). O `StationResponse` traz dados do dono — **não usar na área pública** (criar DTO próprio).
- Três áreas (protótipos): **Admin**, **Produtor** e **Pública**. A área **pública** (sem login) expõe somente estações marcadas como públicas pelo dono: dados de chuva e localização, nunca dados do dono. Endpoints públicos ficam sob um prefixo próprio (ex.: `/api/public/**`) liberado no `SecurityFilterChain`.
- TODO: o TCC 4 (análise/predição) vai consumir dados pela API ou direto no banco?

### Sessão server-side (Spring Session JDBC) — sem JWT

**Decisão:** autenticação por **sessão server-side** com cookie, não JWT. Motivos: API única, uma VPS, cliente só web (SPA) e necessidade de revogação imediata. JWT + refresh token em cookie seria reimplementar sessão à mão (e a rotação de refresh causa logout indevido com requisições paralelas/várias abas). Se surgir cliente não-navegador (app mobile, script do TCC 4), avaliar então um mecanismo de token só para ele.

**Implementado** (`auth.config.SecurityConfig`, `auth.security`, `auth.useCases`). Pontos não óbvios:

- O principal da sessão é `AuthenticatedUser` (Serializable, guardado no Postgres): `getUsername()` devolve o **ID do usuário**, que é o `PRINCIPAL_NAME` usado na revogação (estável se o email mudar). Ao mudar a classe, sessões antigas podem falhar ao desserializar → usuários precisam logar de novo.
- `GET /me` lê o usuário **do banco** (não do principal), então nome/email ficam sempre atuais.
- CSRF: `EagerCsrfTokenRequestHandler` usa o token **cru** (sem máscara XOR) e reemite o cookie em toda resposta; no login o token é rotacionado (`CsrfAuthenticationStrategy`) junto com o ID da sessão (`ChangeSessionIdAuthenticationStrategy`).
- `must_change_password`: `PasswordChangeRequiredInterceptor` responde 403 `PASSWORD_CHANGE_REQUIRED` em `/api/**` exceto `/api/auth/**` e `/api/public/**`.
- Revogação (`UserSessionRevoker`): desativar, redefinir senha (admin) e trocar perfil revogam **todas** as sessões; trocar a própria senha revoga as **outras**.
- Cookies `Secure` controlados por `pluviffar.security.secure-cookies` (true por padrão; `false` em `application-dev.properties`).

- **Spring Session JDBC**: sessões persistidas no Postgres → sobrevivem a restart/deploy e podem ser consultadas/revogadas por usuário.
    - Tabelas `SPRING_SESSION` e `SPRING_SESSION_ATTRIBUTES` criadas por **migration Flyway** (copiar o `schema-postgresql.sql` oficial do Spring Session da versão em uso). `spring.session.jdbc.initialize-schema=never`.
    - Tabelas de sessão no schema `app` (`spring.session.jdbc.table-name=app.SPRING_SESSION`).
- **Cookie de sessão** (`SESSION`): `HttpOnly`, `Secure`, `SameSite=Lax`. Atributos por profile (`dev` em localhost, `prod`).
- **Timeout por inatividade** configurável (`spring.session.timeout`, ex.: `30m`), renovado a cada requisição. "Manter conectado" (remember-me) fica fora por enquanto.
- **Login JSON** (sem form login padrão): `POST /api/auth/login` recebe email + senha, autentica via `AuthenticationManager`, cria a sessão e salva o `SecurityContext` (`HttpSessionSecurityContextRepository` / `SecurityContextRepository`). Regenerar o ID da sessão no login (proteção contra session fixation).
- **Endpoints** (`/api/auth`):
    - `POST /login` — corpo devolve dados básicos do usuário (`CurrentUserResponse`, inclui `mustChangePassword`).
    - `POST /logout` — invalida a sessão e limpa os cookies (idempotente, 204).
    - `GET /me` — usuário logado e role (o front não lê cookie `HttpOnly`, então usa isso).
    - `GET /csrf` — obtém o token CSRF inicial (mesmo valor do cookie).
    - `PUT /password` — troca da própria senha (senha atual + nova).
- **Revogação:** ao desativar usuário ou trocar senha, apagar todas as sessões dele via `FindByIndexNameSessionRepository` (busca por principal). Opcional: limite de sessões simultâneas.
- **CSRF ativo** (auth por cookie exige): `CookieCsrfTokenRepository.withHttpOnlyFalse()` → cookie `XSRF-TOKEN`, front envia header `X-XSRF-TOKEN` em POST/PUT/PATCH/DELETE. Documentar no OpenAPI.
- **CORS:** se front e API ficarem em origens diferentes, `allowCredentials=true` com origens **explícitas** (nunca `*`), via `PLUVIFFAR_CORS_ALLOWED_ORIGINS` (vazio = CORS desligado). Ideal: mesmo site (ex.: `pluviffar.com.br` e `api.pluviffar.com.br`). TODO: definir domínios.
- Senhas com **BCrypt** (`PasswordEncoder`).
- 401 (sem sessão/expirada → front manda para o login) e 403 (sem permissão) padronizados com `ApiErrorDTO` — **sem redirect** para página de login (é API REST): configurar `AuthenticationEntryPoint` e `AccessDeniedHandler` próprios.

### Módulo de usuários

- **Implementado** em `/api/users` (somente `ADMIN`): `POST` (201 + Location), `GET` (paginado; filtros `search`, `role`, `active`; `sort` só em name/email/role/active/createdAt/updatedAt), `GET /{id}`, `PUT /{id}` (nome/email/perfil), `PATCH /{id}/status`, `PUT /{id}/password` (senha provisória). Sem auto-cadastro.
- Regras: email normalizado (trim + minúsculas) e único; ADMIN não pode mudar o próprio perfil nem se desativar (409); senha de 8 a 72 caracteres (limite do BCrypt).
- Campos mínimos: `id` (UUID), `name`, `email` (único, case-insensitive), `password_hash`, `role`, `active`, `created_at`, `updated_at`.
- Seed do primeiro ADMIN via Flyway (ver "Banco de dados").
- Senha inicial definida pelo admin, com **troca obrigatória no primeiro login** (decidido): coluna `users.must_change_password` (default `true`; o ADMIN da seed também nasce com ela ligada).
- Troca da própria senha: `PUT /api/auth/password` (desliga `must_change_password` e revoga as outras sessões). Enquanto a flag estiver ligada, só `/api/auth/**` funciona.

## Contrato da API (OpenAPI) — para levar ao frontend

O frontend será construído depois, também com o Claude, a partir do contrato desta API. Por isso:

- Usar **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui`): Swagger UI em `/swagger-ui.html`, spec em `/v3/api-docs` e `/v3/api-docs.yaml`.
- Manter o spec **versionado no repo** em `docs/openapi.yaml`, regenerado sempre que endpoints/DTOs mudarem (mesmo commit). Esse arquivo é o que vai para o projeto do frontend (e serve para gerar tipos/cliente TS, ex. `openapi-typescript` ou `orval`).
    - Gerado pelo teste `OpenApiSpecExportTest` (salva `/v3/api-docs.yaml` em `docs/openapi.yaml` a cada `./mvnw test`) — decidido. Conferir o diff do arquivo antes de commitar.
- Qualidade do contrato:
    - Todo endpoint com `@Operation` (summary/description em pt-BR), `@Tag` por módulo e as respostas possíveis com `ApiErrorDTO` (schema `ApiError`) como schema de erro.
    - `OpenApiConfig` aplica **por regra** a segurança de cada operação (espelhando o `SecurityConfig`) e as respostas genéricas 400/401/403/500; nos controllers ficam só o sucesso e os erros específicos (404, 409...). Endpoint público novo → ajustar `accessOf()` lá e no `SecurityConfig`.
    - DTOs com `@Schema` (descrição em pt-BR, `example`, campos obrigatórios), nomes em inglês. Enums documentados.
    - Segurança declarada como **cookie de sessão** (`@SecurityScheme` tipo `apiKey`, `in = COOKIE`, nome `SESSION`) + header CSRF `X-XSRF-TOKEN`, e endpoints públicos (`/api/public/**`) marcados sem segurança. Descrever o fluxo csrf → login → me → logout para o front.
    - Paginação e filtros de período (`from`/`to` em ISO-8601) padronizados e documentados.
- Swagger UI e `/v3/api-docs` liberados em dev e **desligados em produção** (`application-prod.properties`); o contrato de produção é o `docs/openapi.yaml` versionado. Reavaliar se precisar do Swagger na VPS.

## Frontend (próxima etapa)

- Em avaliação: **Next.js + HeroUI** (frontend desacoplado) vs. manter tudo no Spring.
- Se desacoplado: configurar CORS, e decidir onde o front será hospedado (mesma VPS?).
- Existem protótipos de tela (áreas Admin / Usuário / Público), incluindo a tela do usuário público. TODO: link ou exportação dos protótipos.
- Referência visual para a tela inicial: https://ecoflex.qualyagro.com.br/

## Fontes de documentação do time

- Pasta "Pluviffar" no Google Drive (Eduardo).
- Espaço "Pluviffar" no Notion (workspace do Prof. Everton Lutz; cópia editável no workspace do Eduardo).
- Relatório de Bordo PluvIFFar 2026.
  Quando algo relevante desses lugares for usado aqui, destilar em `docs/` neste repo.

## Comandos

- Rodar: `./mvnw spring-boot:run` (o profile vem de `SPRING_PROFILES_ACTIVE` no `.env`, via `spring.profiles.active=${SPRING_PROFILES_ACTIVE:}`; ou `-Dspring-boot.run.profiles=dev`). Swagger: `http://localhost:8080/swagger-ui.html`.
- Testes: `./mvnw test` — integração com **Testcontainers** (`timescale/timescaledb:2.25.1-pg18-oss`, mesma versão/edição da produção; precisa de Docker). Não toca no banco de dev. Também regenera `docs/openapi.yaml`.
- Build: `./mvnw clean package` → `target/api-0.0.1-SNAPSHOT.jar`
- Migrations: rodam automaticamente no startup via Flyway.
- Banco de dev: `docker compose up -d` (zerar: `docker compose down -v`).
- psql no banco de dev: `docker compose exec db psql -U pluviffar -d pluviffar`
- A API lê o `.env` via `spring.config.import=optional:file:.env[.properties]` no `application.properties`.

## Como trabalhar neste repo

- Antes de mudanças grandes (novo módulo, mudança de schema, auth), propor um plano curto.
- Mudança no protocolo MQTT ou no schema → atualizar este arquivo (ou `docs/`) no mesmo commit.
- Conversar comigo e comentar em português; código, schema e contratos em inglês (ver "Idioma").
- Feito: schema (V1/V2), entidades JPA, módulo de usuários, auth por sessão + CSRF, infra de erros, contrato OpenAPI, CRUD de gateways e estações com checagem de posse. Próximo: consulta de leituras/status (séries temporais com `time_bucket`, filtros `from`/`to`) e área pública (`/api/public/**`).