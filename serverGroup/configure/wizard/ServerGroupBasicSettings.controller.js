'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus.basicSettings.controller', [])
  .controller('titusServerGroupBasicSettingsCtrl', function($scope, v2modalWizardService) {

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('location');
      } else {
        v2modalWizardService.markDirty('location');
      }
    });

  });
