#!/usr/bin/env bash

echo "Bootstrapping consul for the $1 provider"

{%startup-script-path%}$1/bootstrap-consul.sh
