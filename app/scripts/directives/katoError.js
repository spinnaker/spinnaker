'use strict';


angular.module('deckApp')
  .directive('katoError', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/katoError.html',
      scope: {
        taskStatus: '=',
        title: '@'
      }
    };
  }
);
