'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.details.filter', [])
  .filter('preconditionType', function() {
    return function(input) {
      return input.charAt(0).toUpperCase() + input.slice(1);
    };
  });
