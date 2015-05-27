'use strict';

angular.module('spinnaker')
  .directive('panelProgress', function() {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/directives/panelprogress.html',
      scope: {
        item: '=',
      },
    };
  });
