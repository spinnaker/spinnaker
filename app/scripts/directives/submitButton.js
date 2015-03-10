'use strict';


angular.module('deckApp')
  .directive('submitButton', function () {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/modal/submitButton.html',
      scope: {
        onClick: '&',
        isDisabled: '=',
        isNew: '=',
        submitting: '=',
        label: '=',
      }
    };
  }
);
