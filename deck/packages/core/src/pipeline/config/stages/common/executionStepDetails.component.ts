import { module } from 'angular';

import { ExecutionStepDetails } from './ExecutionStepDetails';
import { angularComponentFromReact } from '../../../../angular/angularComponentFromReact';

export const EXECUTION_STEP_DETAILS_COMPONENT =
  'spinnaker.core.pipeline.config.stages.common.executionStepDetails.component';
module(EXECUTION_STEP_DETAILS_COMPONENT, []).component(
  'executionStepDetails',
  angularComponentFromReact(ExecutionStepDetails, 'executionStepDetails', ['item']),
);
