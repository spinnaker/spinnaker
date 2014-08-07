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
        onChange: '&',
        model: '=sortingModel'
      },
      restrict: 'A',
      controller: function ($scope) {
        if ($scope.default === 'true') {
          $scope.model.sortKey = $scope.key;
        }

        $scope.onChange = $scope.onChange || angular.noop;

        $scope.model.reverse = false;
        $scope.setSortKey = function (key) {
          $scope.model.sortKey = key;
          $scope.model.reverse = $scope.isSortKey(key) ? !$scope.model.reverse : false;
          $scope.onChange();
        };

        $scope.isSortKey = function (key) {
          return $scope.model.sortKey === key;
        };
      }
    };
  }
);
