'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.pipeline', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../caches/cacheInitializer.js'),
  require('../../../../caches/infrastructureCaches.js'),
  require('utils/timeFormatters.js'),
  require('../../services/pipelineConfigService.js'),
  require('./pipelineExecutionDetails.controller.js'),
  require('../../../../applications/applications.read.service.js'),
]).name;
