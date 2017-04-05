cat > /etc/apt/preferences.d/pin-spin-{%artifact%} <<EOL
Package: spinnaker-{%artifact%}
Pin: version {%version%}
Pin-Priority: 1001
EOL
