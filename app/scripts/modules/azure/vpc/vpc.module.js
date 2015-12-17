'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.vpc', [
    require('./vpc.read.service.js')
  ]);
