'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.genericCanary', [
  require('./acaTaskStage'),
  require('./acaTaskExecutionDetails.controller'),
  require('core/serverGroup/serverGroup.read.service.js'),
  require('./acaTaskStage.transformer'),
  require('../canary/canaryScore.directive.js'),
  require('../canary/canaryStatus.directive.js'),
  ACCOUNT_SERVICE,
  require('core/naming/naming.service.js'),
])
  .run(function(pipelineConfig, acaTaskTransformer) {
    pipelineConfig.registerTransformer(acaTaskTransformer);
  });
