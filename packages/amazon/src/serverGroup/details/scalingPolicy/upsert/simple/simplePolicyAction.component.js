import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';

import { SimplePolicyAction } from './SimplePolicyAction';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.upsert.actions.simplePolicy';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT; // for backwards compatibility

module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT, []).component(
  'awsSimplePolicyAction',
  react2angular(withErrorBoundary(SimplePolicyAction, 'awsSimplePolicyAction'), [
    'adjustmentType',
    'adjustmentTypeChanged',
    'operator',
    'scalingAdjustment',
    'updateScalingAdjustment',
  ]),
);
