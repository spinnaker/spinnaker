'use strict';

import { module } from 'angular';

import { CLUSTER_NAME_FILTER } from './clusterName.filter';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';
import { CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYEXECUTIONDETAILS_CONTROLLER } from './deployExecutionDetails.controller';
import { CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE } from './deployStage';
import { CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_TRANSFORMER } from './deployStage.transformer';
import { Registry } from '../../../../registry';

import './deployStage.less';

export const CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE = 'spinnaker.core.pipeline.stage.deploy';
export const name = CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE, [
  CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE,
  CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_TRANSFORMER,
  CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYEXECUTIONDETAILS_CONTROLLER,
  CLUSTER_NAME_FILTER,
  STAGE_COMMON_MODULE,
]).run([
  'deployStageTransformer',
  function (deployStageTransformer) {
    Registry.pipeline.registerTransformer(deployStageTransformer);
  },
]);
