'use strict';
import {CLUSTER_NAME_FILTER} from './clusterName.filter';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy', [
  require('./deployStage.js'),
  require('./deployStage.transformer.js'),
  require('./deployExecutionDetails.controller.js'),
  CLUSTER_NAME_FILTER,
  require('../core/stage.core.module.js'),
])
  .run(function(pipelineConfig, deployStageTransformer) {
    pipelineConfig.registerTransformer(deployStageTransformer);
  });
