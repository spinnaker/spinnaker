import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';
import { MetricSelector } from './MetricSelector';

export const AWS_METRIC_SELECTOR_COMPONENT = 'spinnaker.amazon.scalingPolicy.alarm.metric.selector.component';
module(AWS_METRIC_SELECTOR_COMPONENT, []).component(
  'metricSelector',
  react2angular(withErrorBoundary(MetricSelector, 'metricSelector'), ['alarm', 'serverGroup', 'updateAlarm']),
);
