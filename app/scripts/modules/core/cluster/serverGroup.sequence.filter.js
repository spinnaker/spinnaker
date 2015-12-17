'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.sequence.filter', [
  require('../naming/naming.service.js'),
])
  .filter('serverGroupSequence', function(namingService) {
      return function(input) {
        if (!input) {
          return null;
        }
        return namingService.getSequence(input) || 'n/a';
      };
  });
