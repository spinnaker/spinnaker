import { module } from 'angular';

import { PROJECT_SUMMARY_POD_COMPONENT } from './projectSummaryPod.component';
import { RECENTLY_VIEWED_ITEMS_COMPONENT } from './recentlyViewedItems.component';
import { SEARCH_COMPONENT } from '../widgets/search.component';
import { SEARCH_INFRASTRUCTURE_V2_CONTROLLER } from './infrastructureV2.controller';
import { SEARCH_RESULTS_COMPONENT } from '../searchResult/searchResults.component';
import { SEARCH_RESULT_COMPONENT } from './searchResult.component';
import { SEARCH_RESULT_PODS_COMPONENT } from './searchResultPods.component';

import './infrastructure.less';

export const SEARCH_INFRASTRUCTURE = 'spinnaker.search.infrastructure';
module(SEARCH_INFRASTRUCTURE, [
  require('./infrastructure.controller.js').name,
  PROJECT_SUMMARY_POD_COMPONENT,
  RECENTLY_VIEWED_ITEMS_COMPONENT,
  SEARCH_COMPONENT,
  SEARCH_INFRASTRUCTURE_V2_CONTROLLER,
  SEARCH_RESULTS_COMPONENT,
  SEARCH_RESULT_COMPONENT,
  SEARCH_RESULT_PODS_COMPONENT,
]);
