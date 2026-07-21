import { module } from 'angular';

import { OverrideFailure } from './OverrideFailure';
import { angularComponentFromReact } from '../../../../angular/angularComponentFromReact';

export const OVERRRIDE_FAILURE = 'spinnaker.core.pipeline.stage.overrideFailure';
module(OVERRRIDE_FAILURE, []).component(
  'overrideFailure',
  angularComponentFromReact(OverrideFailure, 'overrideFailure', [
    'failPipeline',
    'continuePipeline',
    'completeOtherBranchesThenFail',
    'updateStageField',
  ]),
);
