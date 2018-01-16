import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CACHE_INITIALIZER_SERVICE } from 'core/cache/cacheInitializer.service';
import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';
import { RECENT_HISTORY_SERVICE } from 'core/history/recentHistory.service';
import { PAGE_TITLE_SERVICE } from 'core/pageTitle/pageTitle.service';
import { INFRASTRUCTURE_SEARCH_SERVICE_V2 } from './infrastructureSearchV2.service';

import { SearchV2 } from 'core/search/infrastructure/SearchV2';

export const SEARCH_INFRASTRUCTURE_V2_CONTROLLER = 'spinnaker.search.infrastructureNew.controller';
module(SEARCH_INFRASTRUCTURE_V2_CONTROLLER, [
  INFRASTRUCTURE_SEARCH_SERVICE_V2,
  RECENT_HISTORY_SERVICE,
  PAGE_TITLE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  OVERRIDE_REGISTRY,
]).component('infrastructureSearchV2', react2angular(SearchV2));
