'use strict';

const angular = require('angular');

import { IMAGE_READER, NAMING_SERVICE, V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.openstack.serverGroup.configure.basicSettings', [
  require('angular-ui-router').default,
  require('angular-ui-bootstrap'),
  V2_MODAL_WIZARD_SERVICE,
  IMAGE_READER,
  NAMING_SERVICE,
])
  .controller('openstackServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state,
                                                           v2modalWizardService, imageReader, namingService) {

     $scope.$watch('form.$valid', function(newVal) {
       if (newVal) {
         v2modalWizardService.markClean('basic-settings');
         v2modalWizardService.markComplete('basic-settings');
       } else {
         v2modalWizardService.markDirty('basic-settings');
       }
     });

     this.imageChanged = (image) => {
       $scope.command.imageName = image.imageName;
       $scope.command.selectedImage = image;
       v2modalWizardService.markClean('basic-settings');
     };

     angular.extend(this, $controller('BasicSettingsMixin', {
       $scope: $scope,
       imageReader: imageReader,
       namingService: namingService,
       $uibModalStack: $uibModalStack,
       $state: $state,
     }));
   });
