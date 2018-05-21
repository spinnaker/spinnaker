'use strict';

const angular = require('angular');

import { SEARCH_INFRASTRUCTURE } from './infrastructure/search.infrastructure.module';
import { INFRASTRUCTURE_STATES } from './infrastructure/infrastructure.states';
import { GLOBAL_SEARCH } from './global/globalSearch.module';

module.exports = angular.module('spinnaker.core.search', [SEARCH_INFRASTRUCTURE, GLOBAL_SEARCH, INFRASTRUCTURE_STATES]);
