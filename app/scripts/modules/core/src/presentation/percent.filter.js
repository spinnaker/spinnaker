'use strict';

const angular = require('angular');

export const CORE_PRESENTATION_PERCENT_FILTER = 'spinnaker.deck.core.filter.percent';
export const name = CORE_PRESENTATION_PERCENT_FILTER; // for backwards compatibility
angular.module(CORE_PRESENTATION_PERCENT_FILTER, []).filter('decimalToPercent', function() {
  return function(decimal) {
    return `${Math.round(decimal * 100)}%`;
  };
});
