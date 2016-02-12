'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.capacity.controller', [])
  .controller('gceServerGroupCapacityCtrl', function($scope, v2modalWizardService) {

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('capacity');
      } else {
        v2modalWizardService.markDirty('capacity');
      }
    });

  });
