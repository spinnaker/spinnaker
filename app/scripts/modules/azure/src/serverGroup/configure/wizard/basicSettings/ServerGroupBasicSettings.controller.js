'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { IMAGE_READER, ModalWizard } from '@spinnaker/core';

import { AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_IMAGE_REGIONAL_FILTER } from './image.regional.filter';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_SERVERGROUPBASICSETTINGS_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.basicSettings';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_SERVERGROUPBASICSETTINGS_CONTROLLER; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_SERVERGROUPBASICSETTINGS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    ANGULAR_UI_BOOTSTRAP,
    AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_IMAGE_REGIONAL_FILTER,
    IMAGE_READER,
  ])
  .controller('azureServerGroupBasicSettingsCtrl', [
    '$scope',
    '$controller',
    '$uibModalStack',
    '$state',
    'imageReader',
    function ($scope, $controller, $uibModalStack, $state, imageReader) {
      $scope.$watch('form.$valid', function (newVal) {
        if (newVal) {
          ModalWizard.markClean('basic-settings');
          ModalWizard.markComplete('basic-settings');
        } else {
          ModalWizard.markIncomplete('basic-settings');
        }
      });

      this.imageChanged = (image) => {
        $scope.command.imageName = image.imageName;
        $scope.command.selectedImage = image;
        ModalWizard.markClean('basic-settings');
      };

      angular.extend(
        this,
        $controller('BasicSettingsMixin', {
          $scope: $scope,
          imageReader: imageReader,
          $uibModalStack: $uibModalStack,
          $state: $state,
        }),
      );

      this.stackPattern = {
        test: function (stack) {
          const pattern = $scope.command.viewState.templatingEnabled ? /^([a-zA-Z0-9]*(\${.+})*)*$/ : /^[a-zA-Z0-9]*$/;

          return pattern.test(stack);
        },
      };

      this.detailPattern = {
        test: function (detail) {
          const pattern = $scope.command.viewState.templatingEnabled
            ? /^([a-zA-Z0-9-]*(\${.+})*)*$/
            : /^[a-zA-Z0-9-]*$/;

          return pattern.test(detail);
        },
      };
    },
  ]);
