'use strict';
import {CLUSTER_NAME_FILTER} from './clusterName.filter';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy', [
  require('./deployStage.js'),
  require('./deployStage.transformer.js'),
  require('./deployExecutionDetails.controller.js'),
  CLUSTER_NAME_FILTER,
  require('../core/stage.core.module.js'),
  require('core/deploymentStrategy/deploymentStrategy.module.js'),
  require('core/account/providerToggles.directive.js'),
])
  .run(function(pipelineConfig, deployStageTransformer) {
    pipelineConfig.registerTransformer(deployStageTransformer);
  });
