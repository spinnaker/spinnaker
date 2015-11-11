'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  require('../../../../core/modal/wizard/modalWizard.service.js'),
  require('../../../../core/utils/rx.js'),
  require('../../../../core/image/image.reader.js'),
  require('../../../../core/naming/naming.service.js'),
])
  .controller('awsServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state,
                                                          modalWizardService, RxService, imageReader, namingService) {

    this.imageChanged = (image) => {
      $scope.command.virtualizationType = image.virtualizationType;
    };

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      RxService: RxService,
      imageReader: imageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('location');
      } else {
        modalWizardService.getWizard().markDirty('location');
      }
    });

  }).name;
