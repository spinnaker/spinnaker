'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titan.resourcesCtrl', [])
  .controller('titanInstanceResourcesCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('resources');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('resources');
      } else {
        modalWizardService.getWizard().markDirty('resources');
      }
    });
  });
