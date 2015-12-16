'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.network', [
    require('./network.read.service.js')
  ]);
