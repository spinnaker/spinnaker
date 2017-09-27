import { module } from 'angular';

import { SEARCH_RESULTS_COMPONENT } from '../searchResult/searchResult.component';
import { SEARCH_COMPONENT } from '../widgets/search.component';
import { SEARCH_INFRASTRUCTURE_V2_CONTROLLER } from './infrastructureV2.controller';

import './infrastructure.less';

export const SEARCH_INFRASTRUCTURE = 'spinnaker.search.infrastructure';
module(SEARCH_INFRASTRUCTURE, [
  require('./infrastructure.controller.js').name,
  SEARCH_INFRASTRUCTURE_V2_CONTROLLER,
  SEARCH_RESULTS_COMPONENT,
  SEARCH_COMPONENT
]);
