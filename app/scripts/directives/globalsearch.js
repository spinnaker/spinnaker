'use strict';


angular.module('deckApp')
  .directive('globalSearch', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
      },
      templateUrl: 'views/globalsearch.html',
      controller: 'GlobalSearchCtrl as ctrl',
    };
  });
