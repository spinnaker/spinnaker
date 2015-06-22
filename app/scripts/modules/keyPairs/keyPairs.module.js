'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.keyPairs', [
    require('./keyParis.read.service.js')
  ]);
