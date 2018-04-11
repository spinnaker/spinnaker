'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.basicSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    V2_MODAL_WIZARD_SERVICE,
  ])
  .controller('ecsServerGroupBasicSettingsCtrl', function(
    $scope,
    $controller,
    $uibModalStack,
    $state,
    v2modalWizardService,
  ) {
    angular.extend(
      this,
      $controller('BasicSettingsMixin', {
        $scope: $scope,
        $uibModalStack: $uibModalStack,
        $state: $state,
      }),
    );

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('location');
        v2modalWizardService.markComplete('location');
      } else {
        v2modalWizardService.markIncomplete('location');
      }
    });
  });
