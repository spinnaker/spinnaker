'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.openstack.basicSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
  ])
  .controller('openstackServerGroupBasicSettingsCtrl', [
    '$scope',
    '$controller',
    '$uibModalStack',
    '$state',
    function($scope, $controller, $uibModalStack, $state) {
      angular.extend(
        this,
        $controller('BasicSettingsMixin', {
          $scope: $scope,
          $uibModalStack: $uibModalStack,
          $state: $state,
        }),
      );

      $scope.subnetFilter = {
        account: $scope.command.account,
        region: $scope.command.region,
      };

      $scope.regionFilter = {
        account: $scope.command.account,
      };

      this.onRegionChanged = function(region) {
        $scope.command.region = region;
        $scope.subnetFilter.region = region;
      };

      $scope.$watch('command.credentials', function(account) {
        $scope.subnetFilter.account = account;
        $scope.subnetFilter.region = $scope.command.region;
      });

      $scope.$watch('command.region', function(region) {
        $scope.subnetFilter.account = $scope.command.credentials;
        $scope.subnetFilter.region = region;
      });

      $scope.$watch('basicSettings.$valid', function(newVal) {
        if (newVal) {
          ModalWizard.markClean('location');
          ModalWizard.markComplete('location');
        } else {
          ModalWizard.markIncomplete('location');
        }
      });
    },
  ]);
