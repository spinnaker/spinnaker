'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.application.platformHealthOverrideCheckbox.directive', [
  require('../../utils/lodash.js'),
])
  .directive('platformHealthOverride', function() {
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
  .directive('initPlatformHealth', function(_) {
    return function (scope, element) {
      angular.element(element).attr('checked', _.isEqual(scope.command.interestingHealthProviderNames, [scope.platformHealthType]));
    };
  })
  .controller('PlatformHealthOverrideCtrl', function($scope) {
    $scope.clicked = function($event) {
      if ($event.currentTarget.checked) {
        $scope.command.interestingHealthProviderNames = [$scope.platformHealthType];
      } else {
        delete $scope.command.interestingHealthProviderNames;
      }
    };
  });
