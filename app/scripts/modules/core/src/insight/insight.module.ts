import {module} from 'angular';

import { COLLAPSIBLE_SECTION_STATE_CACHE } from 'core/cache/collapsibleSectionStateCache';

export const INSIGHT_NGMODULE = module('spinnaker.core.insight', [
  require('angular-ui-router').default,
  COLLAPSIBLE_SECTION_STATE_CACHE,
]);

import './insight.less';

import './insightFilter.component';
import './insightLayout.component';

import './insightmenu.directive';

import './insightFilterState.model';
