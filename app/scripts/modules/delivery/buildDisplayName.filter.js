'use strict';

angular.module('deckApp.delivery.buildDisplayName.filter', [])
  .filter('buildDisplayName', function() {
    return function(input) {
      var formattedInput = '';
      if( input.contains(':') ){
        formattedInput = input.split(':').pop();
      }
      return formattedInput;
    };
  });
