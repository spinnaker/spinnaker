'use strict';

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
        'default': '@',
      },
      restrict: 'A',
      controller: function($scope) {
        if ($scope.default === 'true') {
          $scope.$parent.sortKey = $scope.key;
        };

        // TODO: find a solution that doesn't involve $parent
        $scope.$parent.reverse = false;
        $scope.setSortKey = function(key) {
          $scope.$parent.sortKey = key.toLowerCase();
          $scope.$parent.reverse = $scope.isSortKey(key) ? !$scope.$parent.reverse : false;
        };

        $scope.isSortKey = function(key) {
          return $scope.$parent.sortKey === key.toLowerCase();
        };

        // TODO: fix arrow directionality

      },
    };
  });
