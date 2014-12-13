'use strict';

angular.module('deckApp')
  .directive('availabilityZoneSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'views/application/modal/serverGroup/aws/availabilityZoneDirective.html',
      controller: 'AvailabilityZoneSelectorCtrl as availabilityZoneCtrl',
    }
  })
  .controller('AvailabilityZoneSelectorCtrl', function($scope) {
    $scope.autoBalancingOptions = [
      { label: 'Enabled', value: true},
      { label: 'Manual', value: false}
    ];
  });
