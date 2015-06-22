'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.trigger.pipeline', [
  require('../trigger.module.js'),
  require('../../services/pipelineConfigService.js'),
  require('../../../../applications/applications.read.service.js'),
]);
