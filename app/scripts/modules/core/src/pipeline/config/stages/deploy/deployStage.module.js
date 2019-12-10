'use strict';

const angular = require('angular');

import { CLUSTER_NAME_FILTER } from './clusterName.filter';
import { Registry } from 'core/registry';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

import './deployStage.less';

export const CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE = 'spinnaker.core.pipeline.stage.deploy';
export const name = CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE, [
    require('./deployStage').name,
    require('./deployStage.transformer').name,
    require('./deployExecutionDetails.controller').name,
    CLUSTER_NAME_FILTER,
    STAGE_COMMON_MODULE,
  ])
  .run([
    'deployStageTransformer',
    function(deployStageTransformer) {
      Registry.pipeline.registerTransformer(deployStageTransformer);
    },
  ]);
