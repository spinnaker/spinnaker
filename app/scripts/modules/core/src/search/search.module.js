'use strict';

const angular = require('angular');

import { SEARCH_INFRASTRUCTURE } from './infrastructure/search.infrastructure.module';
import { INFRASTRUCTURE_STATES } from './infrastructure/infrastructure.states';
import { GLOBAL_SEARCH } from './global/globalSearch.module';
import { SEARCH_SERVICE } from './search.service';

module.exports = angular.module('spinnaker.core.search', [
  SEARCH_INFRASTRUCTURE,
  GLOBAL_SEARCH,
  SEARCH_SERVICE,
  INFRASTRUCTURE_STATES,
]);
