'use strict';

const angular = require('angular');

import { SEARCH_INFRASTRUCTURE } from './infrastructure/search.infrastructure.module';
import { INFRASTRUCTURE_STATES } from './infrastructure/infrastructure.states';
import { SEARCH_SERVICE } from './search.service';

module.exports = angular.module('spinnaker.core.search', [
  SEARCH_INFRASTRUCTURE,
  require('./global/globalSearch.module').name,
  SEARCH_SERVICE,
  INFRASTRUCTURE_STATES,
]);
