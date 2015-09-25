'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.network', [
    require('./network.read.service.js')
  ]).name;
