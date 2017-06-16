'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, NAMING_SERVICE } from '@spinnaker/core';
import { CANARY_SCORE_COMPONENT } from '../canary/canaryScore.component';

module.exports = angular.module('spinnaker.canary.genericCanary', [
  require('./acaTaskStage'),
  require('./acaTaskExecutionDetails.controller'),
  require('./acaTaskStage.transformer'),
  CANARY_SCORE_COMPONENT,
  require('../canary/canaryStatus.directive.js'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
])
  .run(function(pipelineConfig, acaTaskTransformer) {
    pipelineConfig.registerTransformer(acaTaskTransformer);
  });
