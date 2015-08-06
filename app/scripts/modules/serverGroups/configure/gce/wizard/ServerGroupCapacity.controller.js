'use strict';

let angular = require('angular');

require('./capacity.html');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.capacity.controller', [])
  .controller('gceServerGroupCapacityCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('capacity');
    modalWizardService.getWizard().markClean('capacity');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('capacity');
      } else {
        modalWizardService.getWizard().markDirty('capacity');
      }
    });

  }).name;
