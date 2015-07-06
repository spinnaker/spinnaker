'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines')
  .filter('clusterName', function(namingService) {
    return function(input) {
      if (!input) {
        return 'n/a';
      }
      return namingService.getClusterName(input.application, input.stack, input.freeFormDetails);
    };
  }).name;
