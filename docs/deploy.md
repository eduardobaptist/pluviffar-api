# Deploy na VPS

Manual para colocar o PluvIFFar novo no ar na VPS (`pluviffar.com.br`, `72.60.10.6`), **substituindo** o que roda hoje.

| Hoje (antigo) | Depois (novo) |
|---|---|
| API em **Node** (serviço systemd `pluviffar-api`, porta 3000) | API **Spring Boot** (serviço systemd `pluviffar-api`, mesmo nome, porta 8080 só em `127.0.0.1`) |
| Banco `pluviffar` com o schema antigo em pt-BR (`dados.*`) | Banco `pluviffar` **recriado do zero**; estrutura criada pelo **Flyway** (V1, V2 = seed do ADMIN, V3) |
| Worker antigo (grava no schema antigo, auto-cadastra estações) | Worker novo (`worker/` deste repo): grava em `readings`/`device_status`/`config_reports` e **ignora** equipamentos não cadastrados |
| Front Vite antigo em `/var/www/pluviffar` | `dist/` do front novo em `/var/www/pluviffar`, servido pelo Apache |
| Apache: `/api` e `/ws` → Node `:3000` | Apache: `/api` → Spring `:8080`; sem `/ws` |

```
Navegador ──:80──▶ Apache
                    ├── /api/*  ──proxy──▶ 127.0.0.1:8080  API Spring ──┐
                    └── resto   ──────────▶ /var/www/pluviffar (dist)    │
                                                                         ▼
Gateway ──MQTT :1883──▶ Mosquitto ──▶ worker (pluvio-worker) ──▶ PostgreSQL + TimescaleDB
```

> **Sem TLS por enquanto (decidido).** Tudo em `http://`. Consequência: senha do login e cookie de sessão trafegam em texto puro. Serve para testes e banca; antes de cadastrar produtores reais, ligar o HTTPS (ver [Ligar o TLS depois](#ligar-o-tls-depois)).

> **Atenção:** o passo 4 **apaga o banco atual** (todas as leituras antigas). O passo 1 faz o backup — não pule.

Convenções deste manual:

- `💻 local` = comandos na sua máquina, na raiz deste repo. `🖥️ VPS` = comandos na VPS (`ssh pluviffar@72.60.10.6`).
- Trechos `<ENTRE_SINAIS>` são para substituir por valores reais. Senhas nunca vão para o repo.

---

## Sumário

0. [Levantamento do que está rodando](#0-levantamento-do-que-está-rodando)
1. [Backup](#1-backup)
2. [Parar worker e API Node](#2-parar-worker-e-api-node)
3. [Remover a API Node](#3-remover-a-api-node)
4. [Recriar o banco do zero](#4-recriar-o-banco-do-zero)
5. [Instalar o Java](#5-instalar-o-java)
6. [Gerar e enviar o jar](#6-gerar-e-enviar-o-jar)
7. [Instalar a API Spring como serviço](#7-instalar-a-api-spring-como-serviço)
8. [Subir a API (Flyway cria o banco)](#8-subir-a-api-flyway-cria-o-banco)
9. [Atualizar o worker](#9-atualizar-o-worker)
10. [Publicar o front](#10-publicar-o-front)
11. [Configurar o Apache](#11-configurar-o-apache)
12. [Primeiro acesso e cadastro dos equipamentos](#12-primeiro-acesso-e-cadastro-dos-equipamentos)
13. [Teste ponta a ponta](#13-teste-ponta-a-ponta)

Depois: [Atualizações futuras](#atualizações-futuras) · [Rollback](#rollback) · [Ligar o TLS depois](#ligar-o-tls-depois) · [Problemas comuns](#problemas-comuns)

---

## 0. Levantamento do que está rodando

**Por quê:** confirmar onde estão os arquivos da API Node e do worker antigo antes de apagar qualquer coisa, e ver se as portas que o novo vai usar estão livres.

🖥️ VPS:

```bash
# unit files: anote WorkingDirectory, ExecStart, User e EnvironmentFile de cada um
systemctl cat pluviffar-api
systemctl cat pluvio-worker

# quem escuta em quais portas (3000 = Node; 8080 precisa estar livre)
sudo ss -ltnp | grep -E ':(80|1883|3000|5432|8080)\b'

# sites ativos no Apache
ls -l /etc/apache2/sites-enabled/

# extensão do Timescale no banco atual (deve aparecer timescaledb 2.25.1)
sudo -u postgres psql -d pluviffar -c '\dx'
```

Anote:

- **pasta da API Node** (`WorkingDirectory` de `pluviffar-api`) → usada nos passos 1 e 3;
- **pasta do worker antigo** (`WorkingDirectory` de `pluvio-worker`). Se for `/opt/pluvio-worker`, o passo 9 atualiza no lugar; se for outra, o passo 9 explica o que fazer.

Se a porta **8080** já estiver ocupada por outra coisa, escolha outra porta para a API (ex.: 8081) e use-a no `.env` (passo 7, `server.port`) e no Apache (passo 11).

---

## 1. Backup

**Por quê:** o passo 4 apaga o banco e o 3 apaga a API Node. Com o backup dá para voltar atrás ([Rollback](#rollback)).

🖥️ VPS:

```bash
mkdir -p ~/backup-deploy && cd ~/backup-deploy

# banco inteiro (formato custom, restaurável com pg_restore)
sudo -u postgres pg_dump -Fc pluviffar > pluviffar-antigo-$(date +%F).dump

# API Node: código + .env + unit file (troque pelo caminho anotado no passo 0)
sudo tar czf api-node-$(date +%F).tar.gz <PASTA_DA_API_NODE> /etc/systemd/system/pluviffar-api.service

# worker antigo (pasta + .env) e configuração do Apache
sudo tar czf worker-antigo-$(date +%F).tar.gz <PASTA_DO_WORKER_ANTIGO> /etc/systemd/system/pluvio-worker.service
sudo tar czf apache-$(date +%F).tar.gz /etc/apache2/sites-available /var/www/pluviffar

ls -lh
```

Confira se o `.dump` tem tamanho coerente (não 0 bytes). Se quiser guardar fora da VPS:

💻 local:

```bash
scp -r pluviffar@72.60.10.6:~/backup-deploy ./backup-vps-$(date +%F)
```

---

## 2. Parar worker e API Node

**Por quê:** os dois estão conectados ao banco. Parados, ninguém grava durante a troca, e o `DROP DATABASE` do passo 4 não briga com conexões abertas.

🖥️ VPS:

```bash
sudo systemctl stop pluvio-worker pluviffar-api
systemctl is-active pluvio-worker pluviffar-api   # os dois devem responder "inactive"
```

A partir daqui o site e a coleta ficam fora do ar até o passo 11. Mensagens MQTT publicadas nesse intervalo **são perdidas** (o worker não usa sessão persistente).

---

## 3. Remover a API Node

**Por quê:** a API Spring usa o **mesmo nome de serviço** (`pluviffar-api`). O unit antigo precisa sumir antes de criar o novo, senão os dois se confundem. O código já está no backup do passo 1.

🖥️ VPS:

```bash
sudo systemctl disable pluviffar-api
sudo rm /etc/systemd/system/pluviffar-api.service
sudo systemctl daemon-reload
sudo systemctl reset-failed pluviffar-api 2>/dev/null || true

# apagar o código da API Node (caminho anotado no passo 0)
sudo rm -rf <PASTA_DA_API_NODE>

# confirmar: nada mais escutando na 3000
sudo ss -ltnp | grep ':3000' || echo "porta 3000 livre"
```

Se a API Node rodava com um usuário de sistema próprio (campo `User=` do unit), dá para removê-lo também: `sudo userdel <USUARIO_NODE>`.

O Node.js em si (`node`/`npm` do apt) **não** precisa ser desinstalado.

> Se a pasta da API Node era `/opt/pluviffar-api`, ela foi apagada aqui e será recriada limpa no passo 7. É o esperado.

---

## 4. Recriar o banco do zero

**Por quê:** o schema antigo (pt-BR, `dados.*`) não tem nada a ver com o novo. Começando de um banco vazio, o Flyway cria tudo do jeito certo e registra o histórico em `flyway_schema_history`.

O que a API faz e o que **não** faz sozinha:

- **não** cria o banco (`CREATE DATABASE`) — feito aqui;
- **não** cria a extensão `timescaledb` (o role `pluviffar` não é superusuário na VPS) — feito aqui;
- **cria** todas as tabelas, hypertables e a seed do ADMIN — passo 8.

🖥️ VPS:

```bash
sudo -u postgres psql <<'SQL'
-- derruba conexões que tenham sobrado e apaga o banco antigo
DROP DATABASE IF EXISTS pluviffar WITH (FORCE);

-- banco novo, vazio, com o pluviffar como dono (o role e a senha dele continuam os mesmos)
CREATE DATABASE pluviffar OWNER pluviffar;

\c pluviffar
-- extensão precisa de superusuário; a migration V1 só confere se ela existe
CREATE EXTENSION timescaledb;
\dx
SQL
```

O `\dx` final deve listar `timescaledb | 2.25.1`. Se der erro dizendo que a biblioteca não está carregada, confira `shared_preload_libraries = 'timescaledb'` em `/etc/postgresql/18/main/postgresql.conf` (já deveria estar, pois o banco antigo usava hypertables).

> O **role** `pluviffar` não é apagado: a senha dele continua a mesma, então o `PG_DSN` do worker e o `SPRING_DATASOURCE_PASSWORD` da API usam a senha que você já tem.

---

## 5. Instalar o Java

**Por quê:** a API é um jar Java 21; só precisa do runtime (JRE), sem compilador.

🖥️ VPS:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless
java -version    # deve mostrar 21.x
```

Se o pacote 21 não existir no Ubuntu 26.04, `openjdk-25-jre-headless` também serve (Java mais novo roda jar de Java 21).

---

## 6. Gerar e enviar o jar

**Por quê:** a VPS não compila nada; o build é feito na sua máquina e só o jar sobe.

💻 local (precisa de Docker rodando, por causa dos testes com Testcontainers):

```bash
./mvnw clean package
ls -lh target/api-0.0.1-SNAPSHOT.jar

scp target/api-0.0.1-SNAPSHOT.jar pluviffar@72.60.10.6:/tmp/api.jar
```

Se precisar gerar sem testes (ex.: sem Docker na hora): `./mvnw clean package -DskipTests`. Evite como rotina — os testes são o que garante que as migrations rodam no Timescale da produção.

---

## 7. Instalar a API Spring como serviço

**Por quê:** igual ao worker: um usuário de sistema sem shell, uma pasta em `/opt`, um `.env` com as senhas e um unit do systemd que mantém o processo vivo e sobe no boot.

### 7.1 Usuário e pasta

🖥️ VPS:

```bash
# cria o usuário só se não existir (a API Node pode ter usado o mesmo nome)
id pluviffar-api >/dev/null 2>&1 \
  || sudo useradd --system --home /opt/pluviffar-api --shell /usr/sbin/nologin pluviffar-api
sudo mkdir -p /opt/pluviffar-api
sudo install -m 644 /tmp/api.jar /opt/pluviffar-api/api.jar
```

Se o usuário **já existia** (sobra da API Node), reaproveite-o, mas confira se ele continua sendo um usuário de serviço:

```bash
getent passwd pluviffar-api   # esperado: ...:/opt/pluviffar-api:/usr/sbin/nologin
id pluviffar-api              # grupos: só pluviffar-api

# se a home ou o shell forem outros, ajuste:
sudo usermod --home /opt/pluviffar-api --shell /usr/sbin/nologin pluviffar-api
# se estiver em grupos extras (ex.: sudo), remova:  sudo gpasswd -d pluviffar-api <GRUPO>

# processos ou crontab que ainda usem esse usuário (devem estar vazios)
pgrep -u pluviffar-api -a; sudo crontab -u pluviffar-api -l 2>/dev/null
```

### 7.2 `.env`

A própria API lê o arquivo `.env` da pasta onde roda (`spring.config.import=optional:file:.env[.properties]`), como **arquivo de properties**. Por isso dá para usar tanto as variáveis do `.env.example` quanto chaves do Spring direto (`server.address=...`).

🖥️ VPS:

```bash
sudo tee /opt/pluviffar-api/.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pluviffar
SPRING_DATASOURCE_USERNAME=pluviffar
SPRING_DATASOURCE_PASSWORD=<SENHA_DO_ROLE_PLUVIFFAR>

# primeiro ADMIN: usado só na 1ª subida (migration V2). Senha com 12+ caracteres.
PLUVIFFAR_ADMIN_EMAIL=<EMAIL_DO_ADMIN>
PLUVIFFAR_ADMIN_PASSWORD=<SENHA_PROVISORIA_DO_ADMIN>

# front e API no mesmo site (pluviffar.com.br/api): sem CORS
PLUVIFFAR_CORS_ALLOWED_ORIGINS=

# sem TLS por enquanto: cookies sem o atributo Secure (voltar para true quando houver https)
pluviffar.security.secure-cookies=false

# só o Apache (mesma máquina) fala com a API; a porta não fica exposta
server.address=127.0.0.1
server.port=8080
EOF

sudo chown -R pluviffar-api:pluviffar-api /opt/pluviffar-api
sudo chmod 600 /opt/pluviffar-api/.env
```

Por que cada linha importa:

| Chave | Se errar... |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | sem o profile, Swagger fica exposto e o `forward-headers-strategy` não vale |
| `SPRING_DATASOURCE_*` | a API não sobe (erro de conexão no log) |
| `PLUVIFFAR_ADMIN_*` | a migration V2 falha e a API não sobe na 1ª vez |
| `pluviffar.security.secure-cookies=false` | o login "funciona", mas o navegador não devolve o cookie por http → 401 em tudo depois |
| `server.address=127.0.0.1` | a API fica acessível direto em `:8080` pela internet, sem passar pelo Apache |

### 7.3 Unit do systemd

Não há `EnvironmentFile=` de propósito: o `.env` tem chaves com ponto (`server.address`), que o systemd não aceita; quem lê o arquivo é a própria API, a partir do `WorkingDirectory`.

🖥️ VPS:

```bash
sudo tee /etc/systemd/system/pluviffar-api.service > /dev/null <<'EOF'
[Unit]
Description=API PluvIFFar (Spring Boot)
After=network-online.target postgresql.service
Wants=network-online.target

[Service]
Type=simple
User=pluviffar-api
Group=pluviffar-api
# a API lê o .env desta pasta
WorkingDirectory=/opt/pluviffar-api
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/pluviffar-api/api.jar
# 143 = encerrada pelo SIGTERM do systemctl stop (desligamento normal da JVM)
SuccessExitStatus=143
Restart=always
RestartSec=5
TimeoutStopSec=30

# endurecimento: a API não escreve em disco
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable pluviffar-api
```

`-Xmx768m` limita a memória da JVM (a VPS tem 8 GB, com Postgres, Mosquitto e Apache juntos). Ajuste se precisar.

---

## 8. Subir a API (Flyway cria o banco)

**Por quê:** na subida, o Flyway vê o banco vazio e aplica as migrations em ordem:

1. `V1__initial_schema.sql` — tabelas da aplicação, hypertables `readings`/`device_status`, `config_reports`, tabelas do Spring Session;
2. `V2__SeedAdmin` — cria o ADMIN com o email/senha do `.env` (troca de senha obrigatória no 1º login);
3. `V3__user_favorite_station.sql` — estação favorita.

🖥️ VPS:

```bash
sudo systemctl start pluviffar-api
journalctl -u pluviffar-api -f
```

Procure no log (Ctrl+C para sair):

- `Migrating schema "public" to version "1 - initial schema"` … `"2 - SeedAdmin"` … `"3 - user favorite station"`
- `Successfully applied 3 migrations to schema "public"`
- `Tomcat started on port 8080`

Conferir no banco:

```bash
sudo -u postgres psql -d pluviffar \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;" \
  -c "SELECT email, role, must_change_password FROM users;" \
  -c "SELECT hypertable_name FROM timescaledb_information.hypertables;"

# a API responde localmente
curl -i http://127.0.0.1:8080/api/public/stations   # 200 e []
```

**Depois que subiu**, a seed já foi aplicada e não roda de novo. Tire a senha provisória do ADMIN do disco:

```bash
sudo sed -i '/^PLUVIFFAR_ADMIN_PASSWORD=/d' /opt/pluviffar-api/.env
```

(Deixe o `PLUVIFFAR_ADMIN_EMAIL`, é inofensivo. Se um dia o banco for recriado de novo, recoloque a senha antes de subir.)

---

## 9. Atualizar o worker

**Por quê:** o worker antigo grava no schema antigo, que não existe mais. O novo (`worker/` deste repo) grava nas tabelas do V1 e só aceita pacotes de equipamentos **cadastrados pela API**.

### 9.1 Enviar o código

💻 local:

```bash
scp -r worker pluviffar@72.60.10.6:~/worker-novo
```

### 9.2 Instalar

O `instalar.sh` é idempotente: cria/atualiza o usuário `pluvio`, copia o código para `/opt/pluvio-worker`, monta o venv, instala o unit `pluvio-worker.service` e **preserva** um `.env` existente (e corrige `MQTT_TOPIC=pluvio/...` antigo para `pluviffar/#`).

🖥️ VPS:

```bash
cd ~/worker-novo
sudo bash instalar.sh
```

### 9.3 Conferir o `.env` do worker

Fica em **`/opt/pluvio-worker/.env`**. Se o worker antigo já estava em `/opt/pluvio-worker`, o `.env` dele foi mantido: compare com o `.env.example` — o novo precisa destas variáveis:

```bash
sudo cat /opt/pluvio-worker/.env
cat ~/worker-novo/.env.example
```

| Variável | Valor |
|---|---|
| `MQTT_HOST` / `MQTT_PORT` | `localhost` / `1883` |
| `MQTT_USER` / `MQTT_PASS` | `pluviometro` / senha do Mosquitto |
| `MQTT_TOPIC` | `pluviffar/#` |
| `MQTT_CLIENT_ID` | `pluvio-worker` |
| `PG_DSN` | `host=localhost port=5432 dbname=pluviffar user=pluviffar password=<SENHA_DO_ROLE_PLUVIFFAR>` |
| `QUEUE_MAX`, `TZ_SEM_OFFSET`, `LOG_LEVEL` | podem ficar como no exemplo |

Se faltar alguma (ex.: o antigo usava outros nomes para o banco), edite e reinicie:

```bash
sudo nano /opt/pluvio-worker/.env
sudo systemctl restart pluvio-worker
journalctl -u pluvio-worker -f
```

No log, espere a conexão com o banco e a assinatura de `pluviffar/#` aceitas. Pacotes de gateways reais vão aparecer como **ignorados** (equipamento desconhecido) até o passo 12 — é o esperado.

### 9.4 Se o worker antigo estava em outra pasta

O `instalar.sh` sobrescreveu o unit `pluvio-worker.service`, então o antigo não roda mais. Só falta apagar a pasta velha (já está no backup do passo 1):

```bash
sudo rm -rf <PASTA_DO_WORKER_ANTIGO>
```

### 9.5 Limpeza

```bash
rm -rf ~/worker-novo /tmp/api.jar
```

---

## 10. Publicar o front

**Por quê:** o front é estático (build do Vite): só arquivos em `dist/` que o Apache entrega. Ele chama a API por caminho **relativo** (`/api/...`), no mesmo domínio — por isso não precisa de CORS.

> O `dist/` publicado precisa ser do front que fala com o **contrato da API Spring** (`docs/openapi.yaml`). O front antigo, feito para a API Node, vai falhar nas chamadas. Se o novo ainda não estiver pronto, dá para seguir com o resto: a API funciona sozinha (passo 12 usa `curl`).

Pontos que o front precisa respeitar para funcionar com esta API (vale conferir antes do build):

- URL base da API **relativa** (`/api`), não `http://localhost:3000` nem `:8080`. Se o projeto usa variável tipo `VITE_API_URL`, deixe vazia/relativa no build de produção;
- requisições com cookie (`credentials: 'include'` no fetch / `withCredentials: true` no axios);
- CSRF: chamar `GET /api/auth/csrf` no início e mandar o header `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN` em todo POST/PUT/PATCH/DELETE.

### 10.1 Build

💻 local, na pasta do projeto do front:

```bash
npm ci
npm run build
ls dist/    # index.html + assets/

# não pode haver source map: o .map traz o código-fonte original (.tsx) dentro dele
find dist -name '*.map'    # tem que sair vazio
```

O Vite **não** gera source map no build por padrão (`build.sourcemap: false`). Se o `find` listar arquivos, alguém ligou no `vite.config.ts`: procure `sourcemap` e deixe `false` (ou remova a linha). `'hidden'` não resolve: tira só o comentário que aponta para o `.map`, mas o arquivo continua sendo gerado.

### 10.2 Envio

💻 local:

```bash
rsync -av --delete --exclude '*.map' dist/ pluviffar@72.60.10.6:/tmp/pluviffar-dist/
```

O `--exclude '*.map'` é uma segunda barreira: mesmo que um build gere mapas, eles não sobem. (Sem `rsync`? `find dist -name '*.map' -delete` antes de `scp -r dist pluviffar@72.60.10.6:/tmp/pluviffar-dist`.)

### 10.3 Troca dos arquivos

🖥️ VPS:

```bash
sudo rsync -a --delete --exclude '*.map' /tmp/pluviffar-dist/ /var/www/pluviffar/
sudo find /var/www/pluviffar -name '*.map' -delete   # sobras de deploys antigos
sudo chown -R root:root /var/www/pluviffar
sudo find /var/www/pluviffar -type d -exec chmod 755 {} +
sudo find /var/www/pluviffar -type f -exec chmod 644 {} +
rm -rf /tmp/pluviffar-dist
```

O `--delete` remove os `assets/index-XXXX.js` do build antigo. Não há problema de cache: o `index.html` é servido com `no-cache` (passo 11), então o navegador sempre pega o build novo.

---

## 11. Configurar o Apache

**Por quê:** o vhost atual manda `/api` e `/ws` para o Node na porta 3000. O novo manda `/api` para o Spring na 8080, e o `/ws` sai (a API Spring não tem WebSocket). A parte do front (DocumentRoot, `FallbackResource`, cache) fica igual.

### 11.1 Módulos

🖥️ VPS:

```bash
sudo a2enmod proxy proxy_http headers
sudo a2dismod proxy_wstunnel   # era só para o /ws do Node
```

### 11.2 Vhost

🖥️ VPS — substitui o `/etc/apache2/sites-available/pluviffar.conf` (o antigo está no backup do passo 1):

```bash
sudo tee /etc/apache2/sites-available/pluviffar.conf > /dev/null <<'EOF'
# PluvIFFar no Apache: front (Vite, arquivos estáticos) + API Spring (127.0.0.1:8080) por proxy.
# Módulos: sudo a2enmod proxy proxy_http headers
# Sem TLS por enquanto (ver docs/deploy.md, "Ligar o TLS depois").
<VirtualHost *:80>
    ServerName pluviffar.com.br
    ServerAlias www.pluviffar.com.br

    DocumentRoot /var/www/pluviffar
    <Directory /var/www/pluviffar>
        Options -Indexes
        AllowOverride None
        Require all granted
        # rotas do front (/gateways, /estacoes...) não são arquivos: abrem o index.html
        FallbackResource /index.html
    </Directory>
    # arquivo de build que não existe é 404 (não o index.html, que quebraria como JS)
    <Directory /var/www/pluviffar/assets>
        FallbackResource disabled
    </Directory>

    # API Spring; o resto é arquivo estático
    ProxyPreserveHost On
    ProxyPass        /api/ http://127.0.0.1:8080/api/
    ProxyPassReverse /api/ http://127.0.0.1:8080/api/

    # assets/index-XXXX.js tem hash no nome e nunca muda; index.html sempre revalida (pega o build novo)
    <LocationMatch "^/assets/">
        Header set Cache-Control "public, max-age=31536000, immutable"
    </LocationMatch>
    <Files "index.html">
        Header set Cache-Control "no-cache"
    </Files>

    # source maps (código-fonte .tsx) nunca são servidos, mesmo que parem na pasta por engano
    <FilesMatch "\.map$">
        Require all denied
    </FilesMatch>

    ErrorLog ${APACHE_LOG_DIR}/pluviffar-error.log
    CustomLog ${APACHE_LOG_DIR}/pluviffar-access.log combined
</VirtualHost>
EOF
```

O que mudou em relação ao antigo:

| Antes | Agora | Motivo |
|---|---|---|
| `ProxyPass /ws ws://127.0.0.1:3000/ws` | removido | a API Spring não tem WebSocket |
| `ProxyPass /api http://127.0.0.1:3000/api` | `ProxyPass /api/ http://127.0.0.1:8080/api/` | nova API, nova porta; com a barra, `/apifoo` não vai para a API por engano |
| — | — | `ProxyPreserveHost` mantido: com o `forward-headers-strategy` do profile `prod`, o `Location` das respostas 201 sai com `pluviffar.com.br` |

### 11.3 Ativar e recarregar

```bash
sudo a2ensite pluviffar
# se o site padrão estiver ativo, ele responde quem acessa pelo IP; desative:
sudo a2dissite 000-default 2>/dev/null || true

sudo apache2ctl configtest     # tem que dizer "Syntax OK"
sudo systemctl reload apache2
```

### 11.4 Conferir

💻 local:

```bash
curl -i http://pluviffar.com.br/api/public/stations   # 200, [] (JSON)
curl -I http://pluviffar.com.br/                       # 200, text/html (front)
curl -I http://pluviffar.com.br/gateways               # 200 (FallbackResource → index.html)
curl -i http://pluviffar.com.br/api/auth/csrf          # 200; Set-Cookie XSRF-TOKEN SEM "Secure"
curl -i http://pluviffar.com.br:8080/ --max-time 5     # deve FALHAR (8080 não exposta)
```

---

## 12. Primeiro acesso e cadastro dos equipamentos

**Por quê:** banco novo = só o ADMIN existe, e o worker ignora qualquer equipamento não cadastrado. Até cadastrar produtor → gateway → estação, nenhuma leitura é gravada.

Se o front novo já estiver no ar, faça tudo por ele. Senão, pelo `curl` (o Swagger fica desligado em produção). A API usa cookie de sessão + CSRF, então os comandos abaixo guardam os cookies num arquivo e mandam o token em cada escrita.

💻 local (ou na VPS):

```bash
API=http://pluviffar.com.br/api
JAR=$(mktemp)

# token CSRF atual (o cookie XSRF-TOKEN é reemitido a cada resposta)
xsrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$JAR"; }
# api MÉTODO /caminho ['json']
api() {
  curl -sS -b "$JAR" -c "$JAR" -X "$1" \
       -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
       "$API$2" ${3:+-d "$3"}
  echo
}

api GET /auth/csrf
```

### 12.1 Login do ADMIN e troca da senha provisória

```bash
api POST /auth/login '{"email":"<EMAIL_DO_ADMIN>","password":"<SENHA_PROVISORIA_DO_ADMIN>"}'
# resposta traz "mustChangePassword": true → até trocar, só /api/auth/** funciona

api PUT /auth/password '{"currentPassword":"<SENHA_PROVISORIA_DO_ADMIN>","newPassword":"<NOVA_SENHA_DO_ADMIN>"}'
api GET /auth/me
```

### 12.2 Produtor

```bash
api POST /users '{"name":"<NOME>","email":"<EMAIL_DO_PRODUTOR>","role":"PRODUCER","password":"<SENHA_PROVISORIA>"}'
```

Anote o `id` da resposta (`<ID_DO_PRODUTOR>`). O produtor também troca a senha no primeiro login.

### 12.3 Gateway

O `hardwareId` é o que aparece na página de configuração do gateway (ex.: `gt-570db4`), o mesmo do tópico MQTT `pluviffar/gt-570db4/...`.

```bash
api POST /gateways '{"hardwareId":"gt-570db4","name":"Gateway sede","ownerId":"<ID_DO_PRODUTOR>"}'
```

Anote o `id` (`<ID_DO_GATEWAY>`).

### 12.4 Estação (pluviômetro)

O `hardwareId` é o campo `id` do payload (ex.: `580db2`). Para aparecer na área pública, `isPublic: true` exige latitude e longitude.

```bash
api POST /stations '{"hardwareId":"580db2","gatewayId":"<ID_DO_GATEWAY>","name":"Pluviômetro lavoura","latitude":-29.0,"longitude":-55.0,"isPublic":true}'
```

A partir daqui o worker passa a gravar o que esse gateway/pluviômetro publicar.

```bash
api POST /auth/logout
rm -f "$JAR"
```

---

## 13. Teste ponta a ponta

**Por quê:** confirmar a cadeia inteira (MQTT → worker → banco → API → Apache) sem depender do hardware.

🖥️ VPS — publica uma leitura falsa como se fosse o gateway cadastrado:

```bash
sudo apt install -y mosquitto-clients   # se ainda não tiver

mosquitto_pub -h localhost -u pluviometro -P '<SENHA_MQTT>' -q 1 \
  -t pluviffar/gt-570db4/dados \
  -m "{\"id\":\"580db2\",\"tips_por_minuto\":4,\"mm\":0.8,\"timestamp\":$(date +%s),\"seq\":1}"

journalctl -u pluvio-worker -n 20 --no-pager
sudo -u postgres psql -d pluviffar -c "SELECT * FROM readings ORDER BY measured_at DESC LIMIT 5;"
```

💻 local — pela área pública (se a estação foi criada com `isPublic: true`):

```bash
curl -s http://pluviffar.com.br/api/public/stations
```

Tem que aparecer a estação com a última leitura e a chuva das últimas 24 h.

Checklist final:

- [ ] `systemctl is-active pluviffar-api pluvio-worker apache2 mosquitto postgresql` → todos `active`
- [ ] `systemctl is-enabled pluviffar-api pluvio-worker` → `enabled` (sobem no boot)
- [ ] front abre em `http://pluviffar.com.br` e login funciona (cookie aceito)
- [ ] leitura de teste aparece em `readings` e na API
- [ ] porta 8080 e 3000 inacessíveis de fora
- [ ] senha provisória do ADMIN removida do `.env` (passo 8) e trocada (passo 12)

Se quiser apagar a leitura falsa do teste: `sudo -u postgres psql -d pluviffar -c "DELETE FROM readings WHERE seq = 1 AND precipitation_mm = 0.8;"` (confira antes com `SELECT` que só ela casa).

---

## Atualizações futuras

### API

💻 local:

```bash
./mvnw clean package
scp target/api-0.0.1-SNAPSHOT.jar pluviffar@72.60.10.6:/tmp/api.jar
```

🖥️ VPS:

```bash
sudo install -m 644 -o pluviffar-api -g pluviffar-api /tmp/api.jar /opt/pluviffar-api/api.jar
sudo systemctl restart pluviffar-api
journalctl -u pluviffar-api -f
```

Migrations novas (`V4__...`) rodam sozinhas no restart. Antes de subir uma migration, faça um `pg_dump` (passo 1): migration aplicada não se desfaz sozinha.

### Worker

💻 local: `scp -r worker pluviffar@72.60.10.6:~/worker-novo` · 🖥️ VPS: `cd ~/worker-novo && sudo bash instalar.sh && rm -rf ~/worker-novo` (o `.env` é preservado).

### Front

Repetir o [passo 10](#10-publicar-o-front) (build → rsync → troca). Não precisa mexer no Apache.

---

## Rollback

Voltar para o sistema antigo (Node + banco antigo + worker antigo), usando o backup do passo 1:

🖥️ VPS:

```bash
cd ~/backup-deploy

# 1. parar e remover o novo
sudo systemctl stop pluviffar-api pluvio-worker
sudo systemctl disable pluviffar-api
sudo rm /etc/systemd/system/pluviffar-api.service

# 2. banco antigo
sudo -u postgres psql -c "DROP DATABASE pluviffar WITH (FORCE);" \
                      -c "CREATE DATABASE pluviffar OWNER pluviffar;"
sudo -u postgres psql -d pluviffar -c "CREATE EXTENSION timescaledb;"
sudo -u postgres pg_restore -d pluviffar --no-owner --role=pluviffar pluviffar-antigo-<DATA>.dump

# 3. API Node, worker antigo e Apache (os tar guardam caminhos absolutos)
sudo tar xzf api-node-<DATA>.tar.gz -C /
sudo tar xzf worker-antigo-<DATA>.tar.gz -C /
sudo tar xzf apache-<DATA>.tar.gz -C /
sudo a2enmod proxy_wstunnel

sudo systemctl daemon-reload
sudo systemctl enable --now pluviffar-api pluvio-worker
sudo apache2ctl configtest && sudo systemctl reload apache2
```

Restaurar dump de banco com TimescaleDB pode exigir `SELECT timescaledb_pre_restore();` antes e `SELECT timescaledb_post_restore();` depois do `pg_restore` (rodar no banco `pluviffar` como `postgres`). Se o `pg_restore` reclamar de hypertables/catálogo do Timescale, é isso.

---

## Ligar o TLS depois

Quando decidir pelo HTTPS (DNS de `pluviffar.com.br` e `www` já apontando para a VPS):

1. Certificado (o certbot cria o vhost `:443` e o redirect de http → https):
   ```bash
   sudo apt install -y certbot python3-certbot-apache
   sudo a2enmod ssl
   sudo certbot --apache -d pluviffar.com.br -d www.pluviffar.com.br
   ```
2. No vhost `:443` gerado (`pluviffar-le-ssl.conf`), junto do `ProxyPass`:
   ```apache
   RequestHeader set X-Forwarded-Proto "https"
   ```
3. Cookies `Secure` de volta: no `/opt/pluviffar-api/.env`, trocar para `pluviffar.security.secure-cookies=true` (ou apagar a linha; `true` é o padrão).
4. `sudo systemctl reload apache2 && sudo systemctl restart pluviffar-api`.
5. Renovação: o certbot instala um timer (`systemctl list-timers | grep certbot`).

O MQTT na 1883 também está sem TLS (pendência separada: Mosquitto em 8883 com certificado, e firmware do gateway com suporte).

---

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| API não sobe: `Seed do ADMIN: defina PLUVIFFAR_ADMIN_EMAIL...` | `.env` sem email/senha do ADMIN na 1ª subida | preencher (senha 12+ caracteres) e `systemctl restart pluviffar-api` |
| API não sobe: `Extensão timescaledb não instalada neste banco` | passo 4 incompleto | `sudo -u postgres psql -d pluviffar -c 'CREATE EXTENSION timescaledb;'` |
| API não sobe: `password authentication failed for user "pluviffar"` | senha errada no `SPRING_DATASOURCE_PASSWORD` | corrigir o `.env` |
| API não sobe: `Port 8080 was already in use` | outro processo na 8080 | `sudo ss -ltnp \| grep 8080`; mudar `server.port` e o Apache |
| `Validate failed: Migrations have failed validation` | migration já aplicada foi alterada no código | nunca editar migration aplicada; criar uma nova `V<n>__...` |
| Apache responde **503** em `/api` | API parada ou ainda subindo | `systemctl status pluviffar-api`, `journalctl -u pluviffar-api -n 50` |
| Login dá 200, mas tudo depois dá **401** | cookie `Secure` em http | `pluviffar.security.secure-cookies=false` no `.env` (passo 7.2) |
| POST/PUT dão **403** `CSRF_TOKEN_INVALID` | front não manda `X-XSRF-TOKEN` | chamar `GET /api/auth/csrf` e enviar o header com o valor do cookie `XSRF-TOKEN` |
| Tudo dá **403** `PASSWORD_CHANGE_REQUIRED` | usuário ainda com senha provisória | `PUT /api/auth/password` |
| Rota do front (`/gateways`) dá 404 ao recarregar | `FallbackResource` ausente / site errado ativo | conferir vhost e `ls /etc/apache2/sites-enabled/` |
| Página em branco, console com erro de JS em `assets/` | `dist/` incompleto ou cache antigo | refazer o passo 10; Ctrl+Shift+R |
| Worker no ar, mas nada em `readings` | equipamento não cadastrado (worker ignora) ou `hardwareId` diferente | conferir log do worker e o cadastro (passo 12): gateway do **tópico** e estação do campo `id` |
| Worker: `not authorised` na assinatura | `MQTT_TOPIC` fora de `pluviffar/#` ou usuário MQTT errado | corrigir `/opt/pluvio-worker/.env` |

Logs:

```bash
journalctl -u pluviffar-api -f
journalctl -u pluvio-worker -f
sudo tail -f /var/log/apache2/pluviffar-error.log /var/log/apache2/pluviffar-access.log
sudo tail -f /var/log/mosquitto/mosquitto.log
```
