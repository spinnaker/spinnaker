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

cat > /etc/consul.d/server.json <<EOL
{
    "bootstrap": false,
    "bootstrap_expect": 3,
    "server": true,
    "datacenter": "spinnaker",
    "data_dir": "/var/consul",
    "log_level": "INFO",
    "enable_syslog": true
}
EOL

resolvconf -u

# Add upstart entry

cat > /etc/init/consul.conf <<EOL
description "Consul server"

start on (local-filesystems and net-device-up IFACE=eth0)
stop on runlevel [!12345]

respawn

exec consul agent -server -config-dir /etc/consul.d -client 0.0.0.0
EOL
