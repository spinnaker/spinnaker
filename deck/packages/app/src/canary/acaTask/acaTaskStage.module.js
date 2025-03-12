'use strict';

import { module } from 'angular';

import { Registry } from '@spinnaker/core';

import { CANARY_ACATASK_ACATASKEXECUTIONDETAILS_CONTROLLER } from './acaTaskExecutionDetails.controller';
import { CANARY_ACATASK_ACATASKSTAGE } from './acaTaskStage';
import { CANARY_ACATASK_ACATASKSTAGE_TRANSFORMER } from './acaTaskStage.transformer';
import { CANARY_SCORE_COMPONENT } from '../canary/canaryScore.component';
import { CANARY_CANARY_CANARYSTATUS_DIRECTIVE } from '../canary/canaryStatus.directive';

export const CANARY_ACATASK_ACATASKSTAGE_MODULE = 'spinnaker.canary.genericCanary';
export const name = CANARY_ACATASK_ACATASKSTAGE_MODULE; // for backwards compatibility
module(CANARY_ACATASK_ACATASKSTAGE_MODULE, [
  CANARY_ACATASK_ACATASKSTAGE,
  CANARY_ACATASK_ACATASKEXECUTIONDETAILS_CONTROLLER,
  CANARY_ACATASK_ACATASKSTAGE_TRANSFORMER,
  CANARY_SCORE_COMPONENT,
  CANARY_CANARY_CANARYSTATUS_DIRECTIVE,
]).run([
  'acaTaskTransformer',
  function (acaTaskTransformer) {
    Registry.pipeline.registerTransformer(acaTaskTransformer);
  },
]);
