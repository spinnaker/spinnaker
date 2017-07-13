import { module } from 'angular';

import { COLLAPSIBLE_SECTION_STATE_CACHE } from 'core/cache/collapsibleSectionStateCache';
import { INSIGHT_LAYOUT_COMPONENT } from './insightLayout.component';

export const INSIGHT_NGMODULE = module('spinnaker.core.insight', [
  require('@uirouter/angularjs').default,
  COLLAPSIBLE_SECTION_STATE_CACHE,
  INSIGHT_LAYOUT_COMPONENT,
]);

import './insight.less';

import './insightFilter.component';

import './insightmenu.directive';

import './insightFilterState.model';
