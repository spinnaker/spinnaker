'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.services', [
  require('./dirtyPipelineTracker.service.js'),
  require('./pipelineConfigService.js'),
]);
