'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.tasks.monitor', [
    require('./taskMonitor.directive.js')
  ]);
