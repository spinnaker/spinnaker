'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.basicSettings.controller', [])
  .controller('cfServerGroupBasicSettingsCtrl', function($scope, modalWizardService) {

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('location');
      } else {
        modalWizardService.getWizard().markDirty('location');
      }
    });

  });
