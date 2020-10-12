'use strict';

import * as angular from 'angular';

export const CORE_NAVIGATION_STATEACTIVE_DIRECTIVE = 'spinnaker.core.navigation.stateActive.directive';
export const name = CORE_NAVIGATION_STATEACTIVE_DIRECTIVE; // for backwards compatibility
angular.module(CORE_NAVIGATION_STATEACTIVE_DIRECTIVE, []).directive('stateActive', function () {
  return {
    restrict: 'A',
    controller: [
      '$scope',
      '$element',
      '$attrs',
      '$state',
      function ($scope, $element, $attrs, $state) {
        const element = angular.element($element[0]);

        $scope.$on('$stateChangeSuccess', function () {
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
