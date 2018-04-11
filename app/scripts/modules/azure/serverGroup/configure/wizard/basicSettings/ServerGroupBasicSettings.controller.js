'use strict';

import { IMAGE_READER, V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.basicSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    require('./image.regional.filter.js').name,
    V2_MODAL_WIZARD_SERVICE,
    IMAGE_READER,
  ])
  .controller('azureServerGroupBasicSettingsCtrl', function(
    $scope,
    $controller,
    $uibModalStack,
    $state,
    v2modalWizardService,
    imageReader,
  ) {
    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('basic-settings');
        v2modalWizardService.markComplete('basic-settings');
      } else {
        v2modalWizardService.markIncomplete('basic-settings');
      }
    });

    this.imageChanged = image => {
      $scope.command.imageName = image.imageName;
      $scope.command.selectedImage = image;
      v2modalWizardService.markClean('basic-settings');
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
  });
