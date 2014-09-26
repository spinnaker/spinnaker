'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .filter('robotToHuman', function() {
    return function(input) {

      var formattedInput = input.charAt(0).toUpperCase() + input.substr(1);

      // clear camel case.
      formattedInput = formattedInput.replace(/[A-Z]/g, ' $&');

      // clear snake case
      formattedInput = formattedInput.replace(/_[a-z]/g, function (str) {
        return ' ' + str.charAt(1).toUpperCase() + str.substr(2);
      });

      // then clear dash case
      formattedInput = formattedInput.replace(/-[a-z]/g, function (str) {
        return ' ' + str.charAt(1).toUpperCase() + str.substr(2);
      });

      formattedInput = formattedInput.replace(/([A-Z])\s([A-Z])\s/g, '$1$2');

      return formattedInput;
    };
  });
