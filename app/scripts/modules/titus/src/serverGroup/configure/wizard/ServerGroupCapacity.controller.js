'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.titus.capacity.controller', [])
  .controller('titusServerGroupCapacityCtrl', function($scope) {
    ModalWizard.markComplete('capacity');
    ModalWizard.markClean('capacity');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        ModalWizard.markClean('capacity');
      } else {
        ModalWizard.markDirty('capacity');
      }
    });
  });
