#!/usr/bin/env bash

set -x
set -e

# Install dependencies required to run Deck's functional tests

# This file is intended to be run inside a debian stretch docker container

echo 'deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main' | tee /etc/apt/sources.list.d/google-chrome.list
curl -o /google_chrome_dev_linux_signing_key.pub https://dl.google.com/linux/linux_signing_key.pub
apt-key add /google_chrome_dev_linux_signing_key.pub
apt-get update
apt-get install -y \
	firefox-esr \
	google-chrome-stable \
	openjdk-8-jdk \
	xvfb
npm install -g yarn
