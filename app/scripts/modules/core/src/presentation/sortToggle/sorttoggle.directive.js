'use strict';

import { module } from 'angular';

export const CORE_PRESENTATION_SORTTOGGLE_SORTTOGGLE_DIRECTIVE = 'spinnaker.core.presentation.sortToggle.directive';
export const name = CORE_PRESENTATION_SORTTOGGLE_SORTTOGGLE_DIRECTIVE; // for backwards compatibility
module(CORE_PRESENTATION_SORTTOGGLE_SORTTOGGLE_DIRECTIVE, []).directive('sortToggle', function () {
  return {
    templateUrl: require('./sorttoggle.directive.html'),
    scope: {
      key: '@',
      label: '@',
      onChange: '&',
      sortModel: '=',
    },
    restrict: 'A',
    controllerAs: 'ctrl',
    controller: [
      '$scope',
      function ($scope) {
        const ctrl = this;

        this.isSortKey = function (key) {
          const field = $scope.sortModel.key;
          return field === key || field === '-' + key;
        };

        this.isReverse = function () {
          return $scope.sortModel.key.indexOf('-') === 0;
        };

        this.setSortKey = function (key, $event) {
          $event.preventDefault();
          const predicate = ctrl.isSortKey(key) && ctrl.isReverse() ? '' : '-';
          $scope.sortModel.key = predicate + key;
          if ($scope.onChange) {
            $scope.onChange();
          }
        };
      },
    ],
  };
});
