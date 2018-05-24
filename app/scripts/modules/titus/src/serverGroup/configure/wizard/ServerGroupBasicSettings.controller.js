'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.titus.basicSettings.controller', [])
  .controller('titusServerGroupBasicSettingsCtrl', function($scope) {
    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        ModalWizard.markClean('location');
      } else {
        ModalWizard.markDirty('location');
      }
    });
  });
