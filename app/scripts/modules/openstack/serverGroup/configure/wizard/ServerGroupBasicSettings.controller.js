'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.configure.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  require('../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../../core/utils/rx.js'),
  require('../../../../core/image/image.reader.js'),
  require('../../../../core/naming/naming.service.js'),
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
