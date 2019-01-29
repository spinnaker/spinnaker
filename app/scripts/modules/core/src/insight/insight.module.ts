import { module } from 'angular';

import './insight.less';

import { INSIGHT_FILTER_COMPONENT } from './insightFilter.component';
import { INSIGHT_FILTER_STATE_MODEL } from './insightFilterState.model';
import { INSIGHT_LAYOUT_COMPONENT } from './insightLayout.component';
import { INSIGHT_MENU_DIRECTIVE } from './insightmenu.directive';

export const INSIGHT_MODULE = 'spinnaker.core.insight.module';
module(INSIGHT_MODULE, [
  INSIGHT_FILTER_STATE_MODEL,
  INSIGHT_LAYOUT_COMPONENT,
  INSIGHT_FILTER_COMPONENT,
  INSIGHT_MENU_DIRECTIVE,
]);
