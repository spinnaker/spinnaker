'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.create', [
  require('./createPipelineButton.controller.js'),
  require('./createPipelineButton.directive.js'),
  require('./createPipelineModal.controller.js'),
]);
