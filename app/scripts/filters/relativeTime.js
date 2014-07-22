'use strict';

angular.module('deckApp')
  .filter('relativeTime', function(momentService) {
    return function(input) {
      return momentService.calendar(input);
    };
  });
