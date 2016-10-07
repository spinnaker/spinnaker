'use strict';

import modalWizardServiceModule from '../../../../core/modal/wizard/v2modalWizard.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.artifactSettings.controller', [
  modalWizardServiceModule,
])
  .controller('cfServerGroupArtifactSettingsCtrl', function(/*$scope, v2modalWizardService*/) {

    // TODO(GLT): Fix roles after Find/Bake updates are rolled in.

    //v2modalWizardService.markComplete('artifact');
    //
    //$scope.$watch('artifact.$valid', function(newVal) {
    //  if (newVal) {
    //    v2modalWizardService.markClean('artifact');
    //    v2modalWizardService.markComplete('artifact');
    //  } else {
    //    v2modalWizardService.markIncomplete('artifact');
    //  }
    //});

  });
