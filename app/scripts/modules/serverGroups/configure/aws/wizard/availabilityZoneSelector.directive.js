'use strict';

//BEN_TODO

module.exports = function() {
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
};
