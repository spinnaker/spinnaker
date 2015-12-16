'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.serverGroupCapacityCtrl', [])
  .controller('azureServerGroupCapacityCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('capacity');
    modalWizardService.getWizard().markClean('capacity');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('capacity');
      } else {
        modalWizardService.getWizard().markDirty('capacity');
      }
    });

  });
