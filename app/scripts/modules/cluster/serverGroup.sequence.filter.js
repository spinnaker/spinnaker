'use strict';

angular.module('spinnaker.serverGroup.sequence.filter', [
  'spinnaker.naming',
])
  .filter('serverGroupSequence', function(namingService) {
      return function(input) {
        if (!input) {
          return null;
        }
        return namingService.getSequence(input) || 'n/a';
      };
  });
