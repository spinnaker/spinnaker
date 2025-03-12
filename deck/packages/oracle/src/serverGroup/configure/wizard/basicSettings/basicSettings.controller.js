'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { IMAGE_READER } from '@spinnaker/core';

export const ORACLE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_BASICSETTINGS_CONTROLLER =
  'spinnaker.oracle.serverGroup.configure.wizard.basicSettings.controller';
export const name = ORACLE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_BASICSETTINGS_CONTROLLER; // for backwards compatibility
angular
  .module(ORACLE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_BASICSETTINGS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    ANGULAR_UI_BOOTSTRAP,
    IMAGE_READER,
  ])
  .controller('oracleServerGroupBasicSettingsCtrl', [
    '$scope',
    '$state',
    '$uibModalStack',
    '$controller',
    'imageReader',
    function ($scope, $state, $uibModalStack, $controller, imageReader) {
      angular.extend(
        this,
        $controller('BasicSettingsMixin', {
          $scope: $scope,
          imageReader: imageReader,
          $uibModalStack: $uibModalStack,
          $state: $state,
        }),
      );
    },
  ]);
