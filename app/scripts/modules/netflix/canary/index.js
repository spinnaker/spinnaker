'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.canary', [
    require('./canaryAnalysisNameSelector.directive.js')
  ]);
