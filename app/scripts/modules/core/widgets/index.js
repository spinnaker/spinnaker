'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widgets', [
    require('./scopeClusterSelector.directive')
  ]);
