'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.genericCanary', [
  require('./acaTaskStage'),
  require('./acaTaskExecutionDetails.controller'),
  require('core/serverGroup/serverGroup.read.service.js'),
  require('./acaTaskStage.transformer'),
  require('../canary/canaryScore.directive.js'),
  require('../canary/canaryStatus.directive.js'),
  require('core/account/account.service.js'),
  require('core/naming/naming.service.js'),
])
  .run(function(pipelineConfig, acaTaskTransformer) {
    pipelineConfig.registerTransformer(acaTaskTransformer);
  });
