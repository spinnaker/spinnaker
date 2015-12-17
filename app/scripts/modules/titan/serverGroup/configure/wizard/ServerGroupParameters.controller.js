'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titan.parameters.controller', [])
  .controller('titanServerGroupParametersCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('parameters');
    modalWizardService.getWizard().markClean('parameters');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('parameters');
      } else {
        modalWizardService.getWizard().markDirty('parameters');
      }
    });

  });
