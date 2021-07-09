import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { CreateScalingPolicyButton } from './CreateScalingPolicyButton';

export const TITUS_CREATE_SCALING_POLICY_BUTTON = 'spinnaker.titus.serverGroup.details.scaling.policy.button';
module(TITUS_CREATE_SCALING_POLICY_BUTTON, []).component(
  'titusCreateScalingPolicyButton',
  react2angular(withErrorBoundary(CreateScalingPolicyButton, 'titusCreateScalingPolicyButton'), [
    'application',
    'serverGroup',
  ]),
);
