VAULT_VERSION={%version%}
VAULT_ARCH=linux_amd64

# Download & unzip specified vault binary
wget https://releases.hashicorp.com/vault/${VAULT_VERSION}/vault_${VAULT_VERSION}_${VAULT_ARCH}.zip

apt-get update
apt-get install unzip -y

unzip vault_${VAULT_VERSION}_${VAULT_ARCH}.zip
rm vault_${VAULT_VERSION}_${VAULT_ARCH}.zip

mv vault /usr/bin
