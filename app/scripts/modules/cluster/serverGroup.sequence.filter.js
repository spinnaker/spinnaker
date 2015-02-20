'use strict';

angular.module('deckApp.serverGroup.sequence.filter', [
  'deckApp.naming',
])
  .filter('serverGroupSequence', function(namingService) {
      return function(input) {
        if (!input) {
          return null;
        }
        return namingService.getSequence(input) || 'n/a';
      };
  });
