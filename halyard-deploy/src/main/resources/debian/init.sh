cat > /lib/systemd/system/spinnaker.service <<EOL
[Unit]
Description=All Spinnaker services
After=network.target
Wants={%systemd-service-configs%}
[Service]
Type=oneshot
ExecStart=/bin/true
RemainAfterExit=yes
[Install]
WantedBy=multi-user.target
EOL
