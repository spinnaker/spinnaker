'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroups.configure.wizard.zoneSelector.directive', [
])
  .directive('azureAvailabilityZoneSelector', function() {
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
