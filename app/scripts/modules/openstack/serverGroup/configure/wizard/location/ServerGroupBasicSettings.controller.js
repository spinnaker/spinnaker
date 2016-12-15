'use strict';

let angular = require('angular');

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.serverGroup.configure.openstack.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  V2_MODAL_WIZARD_SERVICE,
  require('core/image/image.reader.js'),
  NAMING_SERVICE,
])
  .controller('openstackServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state,
                                                          v2modalWizardService, namingService) {

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    $scope.subnetFilter = {
      account: $scope.command.account,
      region: $scope.command.region
    };

    this.onRegionChanged = function(region) {
      $scope.command.region = region;
      $scope.subnetFilter.region = region;
    };

    $scope.$watch('command.credentials', function(account) {
      $scope.subnetFilter.account = account;
    });

    $scope.$watch('basicSettings.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('location');
        v2modalWizardService.markComplete('location');
      } else {
        v2modalWizardService.markIncomplete('location');
      }
    });

  });
