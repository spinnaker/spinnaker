'use strict';

import {ConstraintOptions} from './constraints';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus.parameters.controller', [])
  .controller('titusServerGroupParametersCtrl', function($scope, v2modalWizardService) {

    this.updateConstraints = () => {
      this.hardConstraintOptions = ConstraintOptions.filter(o => !$scope.command.softConstraints.includes(o));
      this.softConstraintOptions = ConstraintOptions.filter(o => !$scope.command.hardConstraints.includes(o));
    };

    this.updateConstraints();

    v2modalWizardService.markComplete('parameters');
    v2modalWizardService.markClean('parameters');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('parameters');
      } else {
        v2modalWizardService.markDirty('parameters');
      }
    });

  });
