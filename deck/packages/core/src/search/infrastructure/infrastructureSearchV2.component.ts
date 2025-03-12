import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SearchV2 } from './SearchV2';
import { CACHE_INITIALIZER_SERVICE } from '../../cache/cacheInitializer.service';
import { OVERRIDE_REGISTRY } from '../../overrideRegistry/override.registry';
import { PAGE_TITLE_SERVICE } from '../../pageTitle/pageTitle.service';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const SEARCH_INFRASTRUCTURE_V2_CONTROLLER = 'spinnaker.search.infrastructureNew.controller';
module(SEARCH_INFRASTRUCTURE_V2_CONTROLLER, [
  PAGE_TITLE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  OVERRIDE_REGISTRY,
]).component('infrastructureSearchV2', react2angular(withErrorBoundary(SearchV2, 'infrastructureSearchV2')));
