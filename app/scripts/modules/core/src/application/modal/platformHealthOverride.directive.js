'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.application.platformHealthOverrideCheckbox.directive', [])
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
  .directive('initPlatformHealth', function() {
    return function(scope, element) {
      angular
        .element(element)
        .attr('checked', _.isEqual(scope.command.interestingHealthProviderNames, [scope.platformHealthType]));
    };
  })
  .controller('PlatformHealthOverrideCtrl', ['$scope', function($scope) {
    $scope.clicked = function($event) {
      if ($event.currentTarget.checked) {
        $scope.command.interestingHealthProviderNames = [$scope.platformHealthType];
      } else {
        delete $scope.command.interestingHealthProviderNames;
      }
    };
  }]);
