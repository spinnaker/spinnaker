'use strict';

let angular = require('angular');
import {IGOR_SERVICE} from 'core/ci/igor.service';

module.exports = angular.module('spinnaker.core.pipeline.stage.jenkins', [
  require('./jenkinsStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('core/utils/timeFormatters.js'),
  IGOR_SERVICE,
  require('./jenkinsExecutionDetails.controller.js'),
]);
