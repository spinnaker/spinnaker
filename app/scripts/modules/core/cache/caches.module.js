'use strict';

import { COLLAPSIBLE_SECTION_STATE_CACHE } from './collapsibleSectionStateCache';
const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.cache', [
    COLLAPSIBLE_SECTION_STATE_CACHE,
  ]);
