'use strict';

import { module } from 'angular';

import { ModalWizard } from '@spinnaker/core';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_IMAGESETTINGS_SERVERGROUPIMAGESETTINGS_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.imageSettings.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_IMAGESETTINGS_SERVERGROUPIMAGESETTINGS_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_IMAGESETTINGS_SERVERGROUPIMAGESETTINGS_CONTROLLER, []).controller(
  'azureServerGroupImageSettingsCtrl',
  [
    '$scope',
    function ($scope) {
      this.clearImage = function () {
        if ($scope.command.image.isCustom == false) {
          $scope.command.image = { isCustom: false };
        } else {
          $scope.command.image.region = $scope.command.region;
        }
      };

      ModalWizard.markComplete('image-settings');

      $scope.$watch('form.$valid', function (newVal) {
        if (newVal) {
          ModalWizard.markClean('image-settings');
        } else {
          ModalWizard.markDirty('image-settings');
        }
      });
    },
  ],
);
