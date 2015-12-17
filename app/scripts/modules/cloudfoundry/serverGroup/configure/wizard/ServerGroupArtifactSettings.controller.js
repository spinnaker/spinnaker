'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.artifactSettings.controller', [
  require('../../../../core/modal/wizard/modalWizard.service.js'),
])
  .controller('cfServerGroupArtifactSettingsCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('artifact');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('artifact');
      } else {
        modalWizardService.getWizard().markDirty('artifact');
      }
    });

  });
