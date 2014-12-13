'use strict';

angular.module('deckApp')
  .filter('filterByStatus', function() {
    return function(executions) {
      return executions; 
    };
  });
