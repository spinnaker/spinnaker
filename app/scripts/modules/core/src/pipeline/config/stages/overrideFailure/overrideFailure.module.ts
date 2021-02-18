import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { OverrideFailure } from './OverrideFailure';

export const OVERRRIDE_FAILURE = 'spinnaker.core.pipeline.stage.overrideFailure';
module(OVERRRIDE_FAILURE, []).component(
  'overrideFailure',
  react2angular(withErrorBoundary(OverrideFailure, 'overrideFailure'), [
    'failPipeline',
    'continuePipeline',
    'completeOtherBranchesThenFail',
    'updateStageField',
  ]),
);
