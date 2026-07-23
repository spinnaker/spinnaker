import { module } from 'angular';

import { InsightLayout } from './InsightLayout';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const INSIGHT_LAYOUT_COMPONENT = 'spinnaker.core.insight.insightLayout.component';
module(INSIGHT_LAYOUT_COMPONENT, []).component(
  'insightLayout',
  angularComponentFromReact(InsightLayout, 'insightLayout', ['app']),
);
