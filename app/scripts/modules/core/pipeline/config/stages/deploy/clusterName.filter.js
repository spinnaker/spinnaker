'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.clusterName.filter', [])
  .filter('clusterName', function(namingService) {
    return function(input) {
      if (!input) {
        return 'n/a';
      }
      return namingService.getClusterName(input.application, input.stack, input.freeFormDetails);
    };
  });
