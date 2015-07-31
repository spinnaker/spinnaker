'use strict';

//BEN_TODO

require('../../views/application/modal/serverGroup/aws/availabilityZoneDirective.html');

module.exports = function() {
  return {
    restrict: 'E',
    scope: {
      command: '=',
    },
    templateUrl: require('../../views/application/modal/serverGroup/aws/availabilityZoneDirective.html'),
    controller: function($scope) {
      $scope.autoBalancingOptions = [
        { label: 'Enabled', value: true},
        { label: 'Manual', value: false}
      ];
    },
  };
};
