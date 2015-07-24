'use strict';

let angular = require('angular');

require('./executionWindows.less');

module.exports = angular.module('spinnaker.pipelines.stage.executionWindows', [
  require('./executionWindowsStage.js'),
  require('./executionWIndows.transform.js'),
  require('./executionWindows.directive.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
])
.name;
