'use strict';

import { module } from 'angular';

import { GLOBAL_SEARCH } from './global/globalSearch.module';
import { INFRASTRUCTURE_STATES } from './infrastructure/infrastructure.states';
import { SEARCH_INFRASTRUCTURE } from './infrastructure/search.infrastructure.module';

export const CORE_SEARCH_SEARCH_MODULE = 'spinnaker.core.search';
export const name = CORE_SEARCH_SEARCH_MODULE; // for backwards compatibility
module(CORE_SEARCH_SEARCH_MODULE, [SEARCH_INFRASTRUCTURE, GLOBAL_SEARCH, INFRASTRUCTURE_STATES]);
