VAULT_VERSION={%version%}
VAULT_ARCH=linux_amd64

# Download & unzip specified vault binary
wget https://releases.hashicorp.com/vault/${VAULT_VERSION}/vault_${VAULT_VERSION}_${VAULT_ARCH}.zip

apt-get update
apt-get install unzip -y

unzip vault_${VAULT_VERSION}_${VAULT_ARCH}.zip
rm vault_${VAULT_VERSION}_${VAULT_ARCH}.zip

mv vault /usr/bin

# Create directory for running vault

mkdir -p /etc/vault.d/
mkdir -p /var/vault

# Setup config

cat > /etc/vault.d/vault.hcl <<EOF
listener "tcp" {
    address     = "0.0.0.0:8200"
    tls_disable = 1
}

storage "file" {
    path = "/var/vault/storage"
}
EOF

# Enable mlock

setcap cap_ipc_lock=+ep $(readlink -f $(which vault))

cat > /etc/init/vault.conf <<EOF
description "Vault server"

start on (local-filesystems and net-device-up IFACE=eth0)
stop on runlevel [!12345]

respawn

exec vault server -config /etc/vault.d/vault.hcl
EOF
