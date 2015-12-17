'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.subnet', [
    require('./subnet.read.service.js')
  ]);
