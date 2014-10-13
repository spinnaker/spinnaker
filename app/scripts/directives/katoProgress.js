'use strict';


angular.module('deckApp')
  .directive('katoProgress', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/katoProgress.html',
      scope: {
        taskStatus: '=',
        title: '@'
      }
    };
  }
);
