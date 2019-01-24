'use strict';

const angular = require('angular');

import { STAGE_CORE_MODULE } from '../core/stage.core.module';
import { TIME_FORMATTERS } from 'core/utils/timeFormatters';

module.exports = angular.module('spinnaker.core.pipeline.stage.jenkins', [
  require('./jenkinsStage').name,
  require('../stage.module').name,
  STAGE_CORE_MODULE,
  TIME_FORMATTERS,
  require('./jenkinsExecutionDetails.controller').name,
]);
