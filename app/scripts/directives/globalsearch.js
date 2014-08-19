'use strict';

var angular = require('angular');

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
