'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.basicSettings.controller', [])
  .controller('cfServerGroupBasicSettingsCtrl', function(/*$scope, v2modalWizardService*/) {

    // TODO(GLT): Fix roles after Find/Bake updates are rolled in.

    //$scope.$watch('basicSettings.$valid', function(newVal) {
    //  if (newVal) {
    //    v2modalWizardService.markClean('location');
    //    v2modalWizardService.markComplete('location');
    //  } else {
    //    v2modalWizardService.markIncomplete('location');
    //  }
    //});

  });
