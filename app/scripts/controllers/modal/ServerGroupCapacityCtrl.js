'use strict';


angular.module('deckApp')
  .controller('ServerGroupCapacityCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('capacity');
    modalWizardService.getWizard().markClean('capacity');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('capacity');
      } else {
        modalWizardService.getWizard().markDirty('capacity');
      }
    });

    $scope.autoBalancingOptions = [
      { label: 'Enabled', value: true},
      { label: 'Manual', value: false}
    ];

    $scope.$watch('command.capacity.desired', function(newVal) {
      if ($scope.state.useSimpleCapacity) {
        $scope.command.capacity.min = newVal;
        $scope.command.capacity.max = newVal;
      }
    });
  });
