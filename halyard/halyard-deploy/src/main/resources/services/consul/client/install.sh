CONSUL_VERSION={%version%}
CONSUL_ARCH=linux_amd64

# Download & unzip specified consul binary
wget https://releases.hashicorp.com/consul/${CONSUL_VERSION}/consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip

apt-get update
apt-get install unzip -y

unzip consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip
rm consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip

mv consul /usr/bin

# Create directory for running consul

mkdir -p /etc/consul.d/
mkdir -p /var/consul

# Add upstart entry
# (Consul is run as root here to bind to port 53 for DNS)

cat > /etc/init/consul.conf <<EOL
description "Consul client"

start on (local-filesystems and net-device-up IFACE=eth0)
stop on runlevel [!12345]

respawn

exec consul agent -config-dir /etc/consul.d -client 0.0.0.0
EOL
