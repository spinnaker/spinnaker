#!/usr/bin/env bash

echo "Mounting config for the $1 provider"

{%startup-script-path%}$1/mount-config.sh
