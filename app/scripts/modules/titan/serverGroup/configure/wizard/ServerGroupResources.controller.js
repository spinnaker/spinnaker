'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titan.resourcesCtrl', [])
  .controller('titanInstanceResourcesCtrl', function($scope, v2modalWizardService) {

    v2modalWizardService.markComplete('resources');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('resources');
      } else {
        v2modalWizardService.markDirty('resources');
      }
    });
  });
