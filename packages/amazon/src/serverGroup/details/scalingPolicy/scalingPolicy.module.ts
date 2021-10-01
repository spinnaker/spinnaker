import { module } from 'angular';

import { AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT } from './alarmBasedSummary.component';
import { SCALING_POLICY_SUMMARY_COMPONENT } from './scalingPolicySummary.component';
import { STEP_POLICY_SUMMARY_COMPONENT } from './stepPolicySummary.component';
import { TARGET_TRACKING_MODULE } from './targetTracking/targetTracking.module';

export const SCALING_POLICY_MODULE = 'spinnaker.amazon.scalingPolicy.module';
module(SCALING_POLICY_MODULE, [
  TARGET_TRACKING_MODULE,
  AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT,
  STEP_POLICY_SUMMARY_COMPONENT,
  SCALING_POLICY_SUMMARY_COMPONENT,
]);
