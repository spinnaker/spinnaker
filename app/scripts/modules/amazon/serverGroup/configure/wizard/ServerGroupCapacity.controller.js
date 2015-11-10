'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.serverGroupCapacityCtrl', [
  require('../../../../core/modal/wizard/modalWizard.service.js'),
])
  .controller('awsServerGroupCapacityCtrl', function($scope, modalWizardService) {

    $scope.setSimpleCapacity = function(simpleCapacity) {
      $scope.command.viewState.useSimpleCapacity = simpleCapacity;
      $scope.command.useSourceCapacity = false;
      $scope.setMinMax($scope.command.capacity.desired);
    };
    $scope.setMinMax = function(newVal) {
      if ($scope.command.viewState.useSimpleCapacity) {
        $scope.command.capacity.min = newVal;
        $scope.command.capacity.max = newVal;
        $scope.command.useSourceCapacity = false;
      }
    };

    modalWizardService.getWizard().markComplete('capacity');
    modalWizardService.getWizard().markClean('capacity');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('capacity');
      } else {
        modalWizardService.getWizard().markDirty('capacity');
      }
    });

  }).name;
