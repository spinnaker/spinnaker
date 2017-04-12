#!/usr/bin/env bash

echo "Mounting config for the $1 provider"

./$1/mount-config.sh
