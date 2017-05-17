'use strict';

import {SEARCH_SERVICE} from './search.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.search', [
  require('./infrastructure/search.infrastructure.module.js'),
  require('./global/globalSearch.module.js'),
  SEARCH_SERVICE,
]);
