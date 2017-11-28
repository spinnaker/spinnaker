source /etc/os-release

if [ "$VERSION_ID" = "14.04" ]; then
  cat > /etc/init/spinnaker.conf <<EOL
description "spinnaker"
start on filesystem or runlevel [2345]
stop on shutdown
pre-start script
  for i in {%services%}
  do
    if [ ! -d "/var/log/spinnaker/\$i" ]; then
      echo "/var/log/spinnaker/\$i does not exist. Creating it..."
      install --mode=755 --owner=spinnaker --group=spinnaker --directory /var/log/spinnaker/\$i
    fi
    service \$i start
  done
end script
EOL
elif [ "$VERSION_ID" = "16.04" ]; then
  cat > /lib/systemd/system/spinnaker.service <<EOL
[Unit]
Description=All Spinnaker services
After=network.target
Wants=gate.service orca.service clouddriver.service front50.service rosco.service igor.service echo.service fiat.service

[Service]
Type=oneshot
ExecStart=/bin/true
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOL
fi
