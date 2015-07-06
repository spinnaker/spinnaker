'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.executionWindows', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./executionWindows.directive.js'),
  require('./executionWindows.transformer.js')
]).name;
