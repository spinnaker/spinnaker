'use strict';

let angular = require('angular');

require('./executionWindows.less');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows', [
  require('./executionWindowsStage.js'),
  require('./executionWindows.transformer.js'),
  require('./executionWindows.directive.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
]);
