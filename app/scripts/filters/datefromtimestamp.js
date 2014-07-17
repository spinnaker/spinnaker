'use strict';

angular.module('deckApp')
  .filter('dateFromTimestamp', function() {
    return function(input) {
      return input;
    };
  });
