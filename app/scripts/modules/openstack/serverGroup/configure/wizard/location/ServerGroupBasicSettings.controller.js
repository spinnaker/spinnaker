'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.openstack.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  require('../../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../../../core/utils/rx.js'),
  require('../../../../../core/image/image.reader.js'),
  require('../../../../../core/naming/naming.service.js'),
])
  .controller('openstackServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state,
                                                          v2modalWizardService, rx, namingService) {

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    this.onRegionChanged = function(region) {
      $scope.command.region = region;
    };

    $scope.$watch('basicSettings.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('location');
        v2modalWizardService.markComplete('location');
      } else {
        v2modalWizardService.markIncomplete('location');
      }
    });

  });
