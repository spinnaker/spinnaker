'use strict';

let angular = require('angular');

require('./pipelineConfig.less');

module.exports = angular.module('spinnaker.pipelines.config', [
  require('./actions/actions.module.js'),
  require('./graph/pipeline.graph.directive.js'),
  require('./services/services.module.js'),
  require('./stages/stage.module.js'),
  require('./triggers/trigger.module.js'),
  require('./parameters/pipeline.module.js'),
  require('./pipelineConfig.controller.js'),
  require('./pipelineConfigView.js'),
  require('./pipelineConfigurer.js'),
  require('./targetSelect.directive.js'),
]).name;
