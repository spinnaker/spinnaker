import { module } from 'angular';

import { DETAILS_SUMMARY } from './detailsSummary.component';
import { TARGET_TRACKING_MODULE } from './targetTracking/targetTracking.module';

export const SCALING_POLICY_MODULE = 'spinnaker.amazon.scalingPolicy.module';
module(SCALING_POLICY_MODULE, [
  DETAILS_SUMMARY,
  TARGET_TRACKING_MODULE,
  require('./alarmBasedSummary.component').name,
]);
