'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.basicSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
  ])
  .controller('ecsServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state) {
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
        ModalWizard.markClean('location');
        ModalWizard.markComplete('location');
      } else {
        ModalWizard.markIncomplete('location');
      }
    });
  });
