'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.keyPairs', [
    require('./keyPairs.read.service.js')
  ]).name;
