import { module } from 'angular';
import { react2angular } from 'react2angular';

import { OverrideFailure } from './OverrideFailure';

export const OVERRRIDE_FAILURE = 'spinnaker.core.pipeline.stage.overrideFailure';
module(OVERRRIDE_FAILURE, []).component(
  'overrideFailure',
  react2angular(OverrideFailure, [
    'failPipeline',
    'continuePipeline',
    'completeOtherBranchesThenFail',
    'updateStageField',
  ]),
);
