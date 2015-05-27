'use strict';


angular.module('spinnaker')
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
