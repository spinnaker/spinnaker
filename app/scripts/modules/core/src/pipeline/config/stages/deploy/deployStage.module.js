'use strict';
import {CLUSTER_NAME_FILTER} from './clusterName.filter';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy', [
  require('./deployStage.js').name,
  require('./deployStage.transformer.js').name,
  require('./deployExecutionDetails.controller.js').name,
  CLUSTER_NAME_FILTER,
  require('../core/stage.core.module.js').name,
])
  .run(function(pipelineConfig, deployStageTransformer) {
    pipelineConfig.registerTransformer(deployStageTransformer);
  });
