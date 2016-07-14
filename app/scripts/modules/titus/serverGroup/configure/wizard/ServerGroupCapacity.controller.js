'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus.capacity.controller', [])
  .controller('titusServerGroupCapacityCtrl', function($scope, v2modalWizardService) {

    v2modalWizardService.markComplete('capacity');
    v2modalWizardService.markClean('capacity');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('capacity');
      } else {
        v2modalWizardService.markDirty('capacity');
      }
    });

  });
