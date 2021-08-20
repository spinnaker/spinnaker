import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';

import { StepPolicyAction } from './StepPolicyAction';

export const STEP_POLICY_ACTION_COMPONENT = 'spinnaker.amazon.scalingPolicy.stepPolicyAction.component';

module(STEP_POLICY_ACTION_COMPONENT, []).component(
  'stepPolicyAction',
  react2angular(withErrorBoundary(StepPolicyAction, 'stepPolicyAction'), [
    'adjustmentType',
    'adjustmentTypeChanged',
    'alarm',
    'isMin',
    'operator',
    'step',
    'stepAdjustments',
    'stepsChanged',
  ]),
);
