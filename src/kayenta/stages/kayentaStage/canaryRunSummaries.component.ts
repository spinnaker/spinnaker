import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import CanaryRunSummaries from './canaryRunSummaries';

export const CANARY_RUN_SUMMARIES_COMPONENT = 'spinnaker.kayenta.canaryRunSummaries.component';
module(CANARY_RUN_SUMMARIES_COMPONENT, []).component(
  'canaryRunSummaries',
  react2angular(withErrorBoundary(CanaryRunSummaries, 'canaryRunSummaries'), ['canaryRuns', 'firstScopeName']),
);
