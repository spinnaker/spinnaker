'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.pipeline', [
  require('./pipelineStage.js').name,
  require('./pipelineExecutionDetails.controller.js').name,
]);
