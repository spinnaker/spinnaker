'use strict';


angular.module('spinnaker')
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
