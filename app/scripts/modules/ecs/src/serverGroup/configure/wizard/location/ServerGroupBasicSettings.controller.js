'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { ModalWizard } from '@spinnaker/core';

export const ECS_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_SERVERGROUPBASICSETTINGS_CONTROLLER =
  'spinnaker.ecs.serverGroup.configure.basicSettings';
export const name = ECS_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_SERVERGROUPBASICSETTINGS_CONTROLLER; // for backwards compatibility
angular
  .module(ECS_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_SERVERGROUPBASICSETTINGS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    ANGULAR_UI_BOOTSTRAP,
  ])
  .controller('ecsServerGroupBasicSettingsCtrl', [
    '$scope',
    '$controller',
    '$uibModalStack',
    '$state',
    function ($scope, $controller, $uibModalStack, $state) {
      angular.extend(
        this,
        $controller('BasicSettingsMixin', {
          $scope: $scope,
          $uibModalStack: $uibModalStack,
          $state: $state,
        }),
      );

      $scope.$watch('form.$valid', function (newVal) {
        if (newVal) {
          ModalWizard.markClean('basicSettings');
          ModalWizard.markComplete('basicSettings');
        } else {
          ModalWizard.markIncomplete('basicSettings');
        }
      });
    },
  ]);
