'use strict';


angular.module('spinnaker')
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
