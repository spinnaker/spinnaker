'use strict';

angular.module('deckApp')
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
