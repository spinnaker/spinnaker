'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {CANARY_SCORE_COMPONENT} from '../canary/canaryScore.component';
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.genericCanary', [
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
