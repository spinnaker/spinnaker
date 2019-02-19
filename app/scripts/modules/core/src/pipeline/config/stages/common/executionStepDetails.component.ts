import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ExecutionStepDetails } from './ExecutionStepDetails';

export const EXECUTION_STEP_DETAILS_COMPONENT =
  'spinnaker.core.pipeline.config.stages.common.executionStepDetails.component';
module(EXECUTION_STEP_DETAILS_COMPONENT, []).component(
  'executionStepDetails',
  react2angular(ExecutionStepDetails, ['item']),
);
