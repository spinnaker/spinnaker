'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.titus.resourcesCtrl', [])
  .controller('titusInstanceResourcesCtrl', function($scope) {
    ModalWizard.markComplete('resources');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        ModalWizard.markClean('resources');
      } else {
        ModalWizard.markDirty('resources');
      }
    });

    $scope.mountPermOptions = [
      { label: 'Read and Write', value: 'RW' },
      { label: 'Read Only', value: 'RO' },
      { label: 'Write Only', value: 'WO' },
    ];
  });
