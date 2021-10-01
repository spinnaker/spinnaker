import { module } from 'angular';

import { SCALING_POLICY_SUMMARY_COMPONENT } from './scalingPolicySummary.component';

export const SCALING_POLICY_MODULE = 'spinnaker.amazon.scalingPolicy.module';
module(SCALING_POLICY_MODULE, [SCALING_POLICY_SUMMARY_COMPONENT]);
