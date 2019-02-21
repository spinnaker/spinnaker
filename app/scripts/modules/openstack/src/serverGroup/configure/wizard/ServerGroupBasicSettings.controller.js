'use strict';

const angular = require('angular');

import { IMAGE_READER, ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.serverGroup.configure.basicSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    IMAGE_READER,
  ])
  .controller('openstackServerGroupBasicSettingsCtrl', ['$scope', '$controller', '$uibModalStack', '$state', 'imageReader', function(
    $scope,
    $controller,
    $uibModalStack,
    $state,
    imageReader,
  ) {
    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        ModalWizard.markClean('basic-settings');
        ModalWizard.markComplete('basic-settings');
      } else {
        ModalWizard.markDirty('basic-settings');
      }
    });

    this.imageChanged = image => {
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
  }]);
