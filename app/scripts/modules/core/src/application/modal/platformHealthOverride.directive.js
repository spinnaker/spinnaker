'use strict';

import * as angular from 'angular';
import _ from 'lodash';

export const CORE_APPLICATION_MODAL_PLATFORMHEALTHOVERRIDE_DIRECTIVE =
  'spinnaker.application.platformHealthOverrideCheckbox.directive';
export const name = CORE_APPLICATION_MODAL_PLATFORMHEALTHOVERRIDE_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_APPLICATION_MODAL_PLATFORMHEALTHOVERRIDE_DIRECTIVE, [])
  .directive('platformHealthOverride', function () {
    return {
      restrict: 'E',
      templateUrl: require('./platformHealthOverrideCheckbox.directive.html'),
      scope: {
        command: '=',
        platformHealthType: '=',
        showHelpDetails: '=',
      },
      controller: 'PlatformHealthOverrideCtrl as platformHealthOverrideCtrl',
    };
  })
  .directive('initPlatformHealth', function () {
    return function (scope, element) {
      angular
        .element(element)
        .attr('checked', _.isEqual(scope.command.interestingHealthProviderNames, [scope.platformHealthType]));
    };
  })
  .controller('PlatformHealthOverrideCtrl', [
    '$scope',
    function ($scope) {
      $scope.clicked = function ($event) {
        if ($event.currentTarget.checked) {
          $scope.command.interestingHealthProviderNames = [$scope.platformHealthType];
        } else {
          delete $scope.command.interestingHealthProviderNames;
        }
      };
    },
  ]);
