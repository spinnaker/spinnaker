/* eslint-disable no-console */
const path = require('path');
const chokidar = require('chokidar');
const WebSocket = require('ws');

// Create a websocket server on port 8999 to live reload the client
const clients = [];
const wss = new WebSocket.Server({ port: 8999 });
wss.on('listening', () => {
  console.log('Websocket (livereload) listening on localhost:8999');
});
wss.on('connection', (ws) => {
  clients.push(ws);
  ws.on('close', () => {
    const index = clients.indexOf(ws);
    if (index !== -1) {
      clients.splice(index, 1);
    }
  });
});

const watchPath = path.resolve('build', 'dist', 'index.js');
const watcher = chokidar.watch(watchPath);
console.log(`Watching ${watchPath} for changes`);
watcher.on('change', (path) => {
  console.log(`${path} changed, reloading ${clients.length} connected browsers`);
  clients.forEach((ws) => {
    try {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send('livereload');
      }
      ws.close();
    } catch (error) {
      console.error(error);
    }
  });
});

function liveReloadJs(req, res, next) {
  res.set('Content-Type', 'application/javascript');
  res.send(`
    export const plugin = {
      initialize() {
        const socket = new WebSocket('ws://localhost:8999/');
        socket.addEventListener('message', function (event) {
          console.log('Message from server ', event.data);
          location.reload();
        });
      }
    }
  `);
}

module.exports = liveReloadJs;
