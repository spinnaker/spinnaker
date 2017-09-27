'use strict';

const angular = require('angular');
import {IGOR_SERVICE} from 'core/ci/igor.service';
import {TIME_FORMATTERS} from 'core/utils/timeFormatters';

module.exports = angular.module('spinnaker.core.pipeline.stage.jenkins', [
  require('./jenkinsStage.js').name,
  require('../stage.module.js').name,
  require('../core/stage.core.module.js').name,
  TIME_FORMATTERS,
  IGOR_SERVICE,
  require('./jenkinsExecutionDetails.controller.js').name,
]);
