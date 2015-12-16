'use strict';

let angular = require('angular');

require('./pipelineConfig.less');

module.exports = angular.module('spinnaker.core.pipeline.config', [
  require('./actions/actions.module.js'),
  require('./graph/pipeline.graph.directive.js'),
  require('./services/services.module.js'),
  require('./stages/stage.module.js'),
  require('./stages/baseProviderStage/baseProviderStage.js'),
  require('./triggers/trigger.module.js'),
  require('./parameters/pipeline.module.js'),
  require('./pipelineConfig.controller.js'),
  require('./pipelineConfigView.js'),
  require('./pipelineConfigurer.js'),
  require('./validation/pipelineConfigValidator.directive.js'),
  require('./targetSelect.directive.js'),
  require('./createNew.directive.js'),
  require('./health/stagePlatformHealthOverride.directive.js'),
]);
