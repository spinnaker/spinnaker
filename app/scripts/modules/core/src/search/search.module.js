'use strict';

const angular = require('angular');

import { INFRASTRUCTURE_STATES } from './infrastructure/infrastructure.states';
import { SEARCH_SERVICE } from './search.service';

module.exports = angular.module('spinnaker.core.search', [
  require('./infrastructure/search.infrastructure.module'),
  require('./global/globalSearch.module'),
  SEARCH_SERVICE,
  INFRASTRUCTURE_STATES,
]);
