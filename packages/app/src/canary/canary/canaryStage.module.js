'use strict';

import { module } from 'angular';

import { Registry } from '@spinnaker/core';

import { CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENT_MODULE } from './canaryDeployment/canaryDeployment.module';
import { CANARY_CANARY_CANARYEXECUTIONDETAILS_CONTROLLER } from './canaryExecutionDetails.controller';
import { CANARY_CANARY_CANARYEXECUTIONSUMMARY_CONTROLLER } from './canaryExecutionSummary.controller';
import { CANARY_SCORE_COMPONENT } from './canaryScore.component';
import { CANARY_SCORES_CONFIG_COMPONENT } from './canaryScores.component';
import { CANARY_CANARY_CANARYSTAGE } from './canaryStage';
import { CANARY_CANARY_CANARYSTAGE_TRANSFORMER } from './canaryStage.transformer';
import { CANARY_CANARY_CANARYSTATUS_DIRECTIVE } from './canaryStatus.directive';

export const CANARY_CANARY_CANARYSTAGE_MODULE = 'spinnaker.canary.stage';
export const name = CANARY_CANARY_CANARYSTAGE_MODULE; // for backwards compatibility
module(CANARY_CANARY_CANARYSTAGE_MODULE, [
  CANARY_CANARY_CANARYSTAGE,
  CANARY_CANARY_CANARYEXECUTIONDETAILS_CONTROLLER,
  CANARY_CANARY_CANARYEXECUTIONSUMMARY_CONTROLLER,
  CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENT_MODULE,
  CANARY_CANARY_CANARYSTAGE_TRANSFORMER,
  CANARY_SCORE_COMPONENT,
  CANARY_SCORES_CONFIG_COMPONENT,
  CANARY_CANARY_CANARYSTATUS_DIRECTIVE,
]).run([
  'canaryStageTransformer',
  function (canaryStageTransformer) {
    Registry.pipeline.registerTransformer(canaryStageTransformer);
  },
]);
