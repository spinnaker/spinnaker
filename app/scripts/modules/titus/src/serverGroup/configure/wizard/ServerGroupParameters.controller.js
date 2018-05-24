'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

import { ConstraintOptions } from './constraints';

module.exports = angular
  .module('spinnaker.serverGroup.configure.titus.parameters.controller', [])
  .controller('titusServerGroupParametersCtrl', function($scope) {
    this.updateConstraints = () => {
      this.hardConstraintOptions = ConstraintOptions.filter(o => !$scope.command.softConstraints.includes(o));
      this.softConstraintOptions = ConstraintOptions.filter(o => !$scope.command.hardConstraints.includes(o));
    };

    this.updateConstraints();

    ModalWizard.markComplete('parameters');
    ModalWizard.markClean('parameters');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        ModalWizard.markClean('parameters');
      } else {
        ModalWizard.markDirty('parameters');
      }
    });

    $scope.migrationPolicyOptions = [
      { label: 'System Default', value: 'systemDefault' },
      { label: 'Self Managed', value: 'selfManaged' },
    ];
  });
