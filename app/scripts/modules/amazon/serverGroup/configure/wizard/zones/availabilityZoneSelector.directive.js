'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroups.configure.aws.wizard.zoneSelector.directive', [
])
  .directive('availabilityZoneSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./availabilityZoneSelector.directive.html'),
      controller: function($scope) {
        $scope.autoBalancingOptions = [
          { label: 'Enabled', value: true},
          { label: 'Manual', value: false}
        ];
      },
    };
});
