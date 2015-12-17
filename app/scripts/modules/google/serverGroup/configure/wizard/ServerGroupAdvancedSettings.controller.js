'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.advancedSetting.controller', [])
  .controller('gceServerGroupAdvancedSettingsCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('advanced');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('advanced');
      } else {
        modalWizardService.getWizard().markDirty('advanced');
      }
    });

  });
