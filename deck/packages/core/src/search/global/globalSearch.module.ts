import { module } from 'angular';

import { GLOBAL_SEARCH_COMPONENT } from './globalsearch.component';

import './globalSearch.less';

export const GLOBAL_SEARCH = 'spinnaker.core.search.global';
module(GLOBAL_SEARCH, [GLOBAL_SEARCH_COMPONENT]);
