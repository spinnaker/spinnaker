import {module} from 'angular';

export const INSIGHT_NGMODULE = module('spinnaker.core.insight', [
  require('angular-ui-router'),
  require('core/cache/collapsibleSectionStateCache'),
]);

import './insight.less';

import './insightFilter.component';
import './insightLayout.component';

import './insightmenu.directive';

import './insightFilterState.model';
