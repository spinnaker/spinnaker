'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.filter.percent', [])
  .filter('decimalToPercent', function () {
    return function (decimal) {
      return `${Math.round(decimal * 100)}%`;
    };
  });
