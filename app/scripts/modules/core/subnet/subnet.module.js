'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.subnet', [
    require('./subnet.read.service.js')
  ]);
