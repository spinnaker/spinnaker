'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.pipeline', [
  require('./pipelineStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../cache/cacheInitializer.js'),
  require('../../../../cache/infrastructureCaches.js'),
  require('../../../../utils/timeFormatters.js'),
  require('../../services/pipelineConfigService.js'),
  require('./pipelineExecutionDetails.controller.js'),
  require('../../../../application/service/applications.read.service.js'),
]);
