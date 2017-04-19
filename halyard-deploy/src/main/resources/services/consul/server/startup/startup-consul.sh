#!/usr/bin/env bash

echo "Bootstrapping consul for the $1 provider"

./$1/bootstrap-consul.sh
