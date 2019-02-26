'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.navigation.stateActive.directive', [])
  .directive('stateActive', function() {
    return {
      restrict: 'A',
      controller: [
        '$scope',
        '$element',
        '$attrs',
        '$state',
        function($scope, $element, $attrs, $state) {
          var element = angular.element($element[0]);

          $scope.$on('$stateChangeSuccess', function() {
            if ($state.includes($attrs.stateActive)) {
              element.addClass('active');
            } else {
              element.removeClass('active');
            }
          });
        },
      ],
    };
  });
