'use strict';

const angular = require('angular');
import { SCRIPT_EXECUTION_DETAILS_CONTROLLER } from './scriptExecutionDetails.controller';

module.exports = angular.module('spinnaker.core.pipeline.stage.script', [
  require('./scriptStage.js'),
  SCRIPT_EXECUTION_DETAILS_CONTROLLER,
]);
