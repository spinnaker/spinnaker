
cat > /etc/init/spinnaker.conf <<EOL
description "spinnaker"

start on filesystem or runlevel [2345]
stop on shutdown

pre-start script

  for i in {%spinnaker-artifacts%}
  do
    if [ ! -d "/var/log/spinnaker/\$i" ]; then
      echo "/var/log/spinnaker/\$i does not exist. Creating it..."
      install --mode=755 --owner=spinnaker --group=spinnaker --directory /var/log/spinnaker/\$i
    fi

    service \$i start
  done
end script
EOL

