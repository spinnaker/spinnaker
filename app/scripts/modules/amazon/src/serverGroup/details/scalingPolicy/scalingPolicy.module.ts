import { module } from 'angular';

import { DETAILS_SUMMARY } from './detailsSummary.component';
import { TARGET_TRACKING_MODULE } from './targetTracking/targetTracking.module';
import { AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT } from './alarmBasedSummary.component';

export const SCALING_POLICY_MODULE = 'spinnaker.amazon.scalingPolicy.module';
module(SCALING_POLICY_MODULE, [
  DETAILS_SUMMARY,
  TARGET_TRACKING_MODULE,
  AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT,
]);
