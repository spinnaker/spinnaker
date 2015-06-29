'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config', [
  require('./pipelineConfigView.js'),
  require('./pipelineConfigurer.js'),
  require('./pipelineConfigProvider.js'),
  require('./pipelineConfig.controller.js'),
  require('./actions/actions.module.js'),
  require('./graph/graph.directive.js'),
  require('./services/services.module.js'),
  require('./triggers/trigger.module.js'),
]);
