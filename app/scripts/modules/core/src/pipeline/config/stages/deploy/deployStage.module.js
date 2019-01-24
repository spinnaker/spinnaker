'use strict';

const angular = require('angular');

import { CLUSTER_NAME_FILTER } from './clusterName.filter';
import { Registry } from 'core/registry';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';

import './deployStage.less';

module.exports = angular
  .module('spinnaker.core.pipeline.stage.deploy', [
    require('./deployStage').name,
    require('./deployStage.transformer').name,
    require('./deployExecutionDetails.controller').name,
    CLUSTER_NAME_FILTER,
    STAGE_CORE_MODULE,
  ])
  .run(function(deployStageTransformer) {
    Registry.pipeline.registerTransformer(deployStageTransformer);
  });
