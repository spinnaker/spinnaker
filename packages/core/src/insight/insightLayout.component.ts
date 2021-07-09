import { module } from 'angular';
import { react2angular } from 'react2angular';

import { InsightLayout } from './InsightLayout';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const INSIGHT_LAYOUT_COMPONENT = 'spinnaker.core.insight.insightLayout.component';
module(INSIGHT_LAYOUT_COMPONENT, []).component(
  'insightLayout',
  react2angular(withErrorBoundary(InsightLayout, 'insightLayout'), ['app']),
);
