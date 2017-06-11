import { module } from 'angular';

import { DETAILS_SUMMARY } from './detailsSummary.component';

export const SCALING_POLICY_MODULE = 'spinnaker.amazon.scalingPolicy.module';
module(SCALING_POLICY_MODULE, [
  DETAILS_SUMMARY,
  require('./alarmBasedSummary.component'),
]);
