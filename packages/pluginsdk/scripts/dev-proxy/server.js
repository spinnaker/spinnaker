#!/usr/bin/env node
/* eslint-disable no-console */

/*********************************
 * dev-proxy: a development proxy for doing local development on a Deck Plugin
 * This proxy allows a Deck Plugin Developer to load their Deck plugin into an existing Spinnaker installation.
 *
 * Point the proxy to the URL of a live spinnaker installation.
 * It will serve the deck assets over http://localhost:9000.
 * It serves a custom copy of `/plugin-manifest.json` which instructs spinnaker to load the plugin assets locally.
 * It also enables live reloading (whenever the plugin assets change) via a websocket connection between the browser and proxy.
 *********************************/
const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
const certs = require('./certs');

console.log('dev-proxy: a development proxy for doing local development on a Deck Plugin');
console.log('This service proxies deck assets from an live deck installation and loads your Deck plugin');
console.log();

const packageJson = JSON.parse(fs.readFileSync('package.json', 'UTF-8'));

const DEV_PROXY_HOST = process.env.DEV_PROXY_HOST || (packageJson.devProxy && packageJson.devProxy.host);

const DEV_PROXY_HTTP_PORT =
  parseInt(process.env.DEV_PROXY_HTTP_PORT || (packageJson.devProxy && packageJson.devProxy.httpPort)) || 9000;
const DEV_PROXY_HTTPS_PORT =
  parseInt(process.env.DEV_PROXY_HTTPS_PORT || (packageJson.devProxy && packageJson.devProxy.httpsPort)) || 9443;

if (!DEV_PROXY_HOST) {
  console.error();
  console.error();
  console.error('Error: No Deck URL specified. Add a field to package.json:');
  console.error();
  console.error('"devProxy": {');
  console.error('  "host": "https://existing.spinnaker.deck.url/"');
  console.error('}');
  console.error();
  console.error('Or, to specify the proxy host on the command line:');
  console.error();
  console.error('DEV_PROXY_HOST=https://existing.spinnaker.deck.url/ yarn proxy');
  console.error();
  console.error();
  process.exit(1);
}
const PLUGIN_ID = process.env.PLUGIN_ID || getPluginId();
console.log(`pluginId: ${PLUGIN_ID}`);
const PLUGIN_DIST_DIR = path.resolve('build', 'dist');

function getPluginId() {
  try {
    const line = fs
      .readFileSync(path.resolve('..', 'build.gradle'))
      .toString()
      .split(/\n/)
      .find((line) => line.includes('pluginId'));

    const [_, pluginId] = /pluginId\s*=\s*"([^"]+)"/.exec(line);
    return pluginId;
  } catch (error) {
    console.error();
    console.error();
    console.error(`Error: Unable to determine pluginId from ${path.resolve('..', 'build.gradle')}`);
    console.error(`Expected to find a line matching 'pluginId = "your.pluginid"`);
    console.error();
    process.exit(2);
  }
}

const app = express();

// Serve a plugin manifest with the plugin details filled in
app.get('/plugin-manifest.json', (req, resp, next) => {
  resp.send([
    {
      id: PLUGIN_ID,
      version: '0.0.0',
      url: `/plugindev/${PLUGIN_ID}/index.js`,
    },
    {
      id: 'plugindev.livereload',
      version: '0.0.0',
      url: `/livereload.js`,
    },
  ]);
});

// Serve the plugin build via /plugindev/*
app.use(`/plugindev/${PLUGIN_ID}/`, express.static(PLUGIN_DIST_DIR));

// Send this livereload code to the client which will load it as a plugin
app.use('/livereload.js', require('./livereload'));

// Serve all other requests to deck code from an existing spinnaker environment
app.use('/', createProxyMiddleware({ target: DEV_PROXY_HOST, changeOrigin: true }));

// http
http.createServer(app).listen(DEV_PROXY_HTTP_PORT);

// https
https
  .createServer(
    {
      key: certs.key,
      cert: certs.cert,
    },
    app,
  )
  .listen(DEV_PROXY_HTTPS_PORT);

console.log(`Server started on http://localhost:${DEV_PROXY_HTTP_PORT}/`);
console.log(`Server started on https://localhost:${DEV_PROXY_HTTPS_PORT}/`);
