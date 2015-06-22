'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    scope: {
      command: '=',
    },
    template: require('views/application/modal/serverGroup/aws/availabilityZoneDirective.html'),
    controller: function($scope) {
      $scope.autoBalancingOptions = [
        { label: 'Enabled', value: true},
        { label: 'Manual', value: false}
      ];
    },
  };
};
