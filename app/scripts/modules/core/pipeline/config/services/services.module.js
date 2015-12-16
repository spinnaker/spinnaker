'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.services', [
  require('./dirtyPipelineTracker.service.js'),
  require('./pipelineConfigService.js'),
]);
