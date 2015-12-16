'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.advancedSetting.controller', [])
  .controller('azureServerGroupAdvancedSettingsCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('advanced');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('advanced');
      } else {
        modalWizardService.getWizard().markDirty('advanced');
      }
    });

  });
