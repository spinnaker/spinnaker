import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { CreateScalingPolicyButton } from './CreateScalingPolicyButton';

export const CREATE_SCALING_POLICY_BUTTON = 'spinnaker.amazon.serverGroup.details.scaling.policy.button';
module(CREATE_SCALING_POLICY_BUTTON, []).component(
  'createScalingPolicyButton',
  react2angular(withErrorBoundary(CreateScalingPolicyButton, 'createScalingPolicyButton'), [
    'application',
    'serverGroup',
  ]),
);
