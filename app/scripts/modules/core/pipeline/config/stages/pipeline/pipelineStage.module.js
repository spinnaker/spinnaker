'use strict';

const angular = require('angular');

import {TIME_FORMATTERS} from 'core/utils/timeFormatters';

module.exports = angular.module('spinnaker.core.pipeline.stage.pipeline', [
  require('./pipelineStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  TIME_FORMATTERS,
  require('./pipelineExecutionDetails.controller.js'),
  require('core/widgets/spelText/spelSelect.component'),
]);
