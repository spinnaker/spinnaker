'use strict';


angular.module('deckApp')
  .directive('validationError', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/validationError.html',
      scope: {
        message: '@'
      }
    };
  }
);
