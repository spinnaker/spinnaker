'use strict';


angular.module('deckApp.validation')
  .directive('validationError', function () {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/validation/validationError.html',
      scope: {
        message: '@'
      }
    };
  }
);
