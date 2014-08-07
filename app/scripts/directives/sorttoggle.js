'use strict';

require('../app');
var angular = require('angular');

/**
 * @ngdoc directive
 * @name scumApp.directive:sortToggle
 * @description
 * # sortToggle
 */

angular.module('deckApp')
  .directive('sortToggle', function () {
    return {
      templateUrl: 'views/sorttoggle.html',
      scope: {
        key: '@',
        label: '@',
        'default': '@',
        onChange: '&'
      },
      restrict: 'A',
      controller: function ($scope) {
        if ($scope.default === 'true') {
          $scope.$parent.sortKey = $scope.key;
        }

        $scope.onChange = $scope.onChange || angular.noop;

        // TODO: find a solution that doesn't involve $parent
        $scope.$parent.reverse = false;
        $scope.setSortKey = function (key) {
          $scope.$parent.sortKey = key;
          $scope.$parent.reverse = $scope.isSortKey(key) ? !$scope.$parent.reverse : false;
          $scope.onChange();
        };

        $scope.isSortKey = function (key) {
          return $scope.$parent.sortKey === key;
        };
      }
    };
  }
);
