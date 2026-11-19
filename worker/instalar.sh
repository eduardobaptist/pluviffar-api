#!/usr/bin/env bash
# Instala o worker PluvIFFar como serviço do systemd em /opt/pluvio-worker.
# Rodar de dentro desta pasta:   sudo bash instalar.sh
# Pode rodar de novo para atualizar o código: preserva o .env existente.
set -euo pipefail

DEST=/opt/pluvio-worker
USUARIO=pluvio
AQUI="$(cd "$(dirname "$0")" && pwd)"

[ "$(id -u)" -eq 0 ] || { echo "rode com sudo: sudo bash instalar.sh"; exit 1; }

echo "==> dependências do sistema"
apt-get install -y -q python3-venv >/dev/null

echo "==> usuário de serviço '$USUARIO'"
id "$USUARIO" >/dev/null 2>&1 || useradd --system --home "$DEST" --shell /usr/sbin/nologin "$USUARIO"

echo "==> arquivos em $DEST"
mkdir -p "$DEST"
install -m 644 "$AQUI/pluvio_worker.py" "$AQUI/requirements.txt" "$DEST/"

echo "==> ambiente Python"
[ -x "$DEST/venv/bin/python" ] || python3 -m venv "$DEST/venv"
"$DEST/venv/bin/pip" install -q --upgrade pip
"$DEST/venv/bin/pip" install -q -r "$DEST/requirements.txt"

NOVO_ENV=0
if [ ! -f "$DEST/.env" ]; then
    install -m 600 "$AQUI/.env.example" "$DEST/.env"
    NOVO_ENV=1
fi
# Protocolo v1.0: o tópico antigo (pluvio/dados) deixou de existir e a ACL não permite mais
if grep -q '^MQTT_TOPIC=pluvio/' "$DEST/.env"; then
    sed -i 's|^MQTT_TOPIC=pluvio/.*|MQTT_TOPIC=pluviffar/#|' "$DEST/.env"
    echo "==> .env: MQTT_TOPIC atualizado para pluviffar/# (protocolo v1.0)"
fi
chown -R "$USUARIO:$USUARIO" "$DEST"
chmod 600 "$DEST/.env"

echo "==> serviço systemd"
install -m 644 "$AQUI/pluvio-worker.service" /etc/systemd/system/pluvio-worker.service
systemctl daemon-reload
systemctl enable pluvio-worker >/dev/null 2>&1

if [ "$NOVO_ENV" -eq 1 ] || grep -q "coloque-a-senha-aqui\|troque-esta-senha" "$DEST/.env"; then
    echo
    echo "Falta configurar as senhas. Edite:"
    echo "    sudo nano $DEST/.env"
    echo "e depois inicie:"
    echo "    sudo systemctl restart pluvio-worker && journalctl -u pluvio-worker -f"
else
    systemctl restart pluvio-worker
    sleep 2
    systemctl --no-pager --lines=8 status pluvio-worker || true
    echo
    echo "Acompanhar:  journalctl -u pluvio-worker -f"
fi