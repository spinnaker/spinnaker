'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.serverGroupCapacityCtrl', [
  require('../../../../../core/modal/wizard/modalWizard.service.js'),
  require('./capacitySelector.directive.js'),
  require('./capacityFooter.directive.js'),
  require('./targetHealthyPercentageSelector.directive.js'),
  require('./azRebalanceSelector.directive.js'),
  require('../availabilityZoneSelector.directive.js'),
])
  .controller('awsServerGroupCapacityCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('capacity');
    modalWizardService.getWizard().markClean('capacity');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('capacity');
      } else {
        modalWizardService.getWizard().markDirty('capacity');
      }
    });

  });
