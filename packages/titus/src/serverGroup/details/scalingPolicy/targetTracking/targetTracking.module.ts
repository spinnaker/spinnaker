import { module } from 'angular';

import { TARGET_TRACKING_CHART_COMPONENT } from './targetTrackingChart.component';
import { TARGET_TRACKING_SUMMARY_COMPONENT } from './targetTrackingSummary.component';

export const TARGET_TRACKING_MODULE = 'spinnaker.titus.scalingPolicy.targetTracking';
module(TARGET_TRACKING_MODULE, [TARGET_TRACKING_CHART_COMPONENT, TARGET_TRACKING_SUMMARY_COMPONENT]);
