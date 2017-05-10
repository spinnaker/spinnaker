'use strict';

let angular = require('angular');

import {EXECUTION_WINDOW_ACTIONS_COMPONENT} from './executionWindowActions.component';

require('./executionWindows.less');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows', [
  require('./executionWindowsStage.js'),
  require('./executionWindows.transformer.js'),
  require('./executionWindows.directive.js'),
  EXECUTION_WINDOW_ACTIONS_COMPONENT,
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
]);
