'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.genericCanary', [
  require('./acaTaskStage'),
  require('./acaTaskExecutionDetails.controller'),
  require('core/serverGroup/serverGroup.read.service.js'),
  require('./acaTaskStage.transformer'),
  require('../canary/canaryScore.directive.js'),
  require('../canary/canaryStatus.directive.js'),
  require('core/account/account.service.js'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
])
  .run(function(pipelineConfig, acaTaskTransformer) {
    pipelineConfig.registerTransformer(acaTaskTransformer);
  });
