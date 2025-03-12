'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';

import { AccountService, InfrastructureCaches } from '@spinnaker/core';

export const GOOGLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER =
  'spinnaker.gce.securityGroup.create.controller';
export const name = GOOGLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER, [UIROUTER_ANGULARJS])
  .controller('gceCreateSecurityGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    '$state',
    '$controller',
    'application',
    'securityGroup',
    function ($scope, $uibModalInstance, $state, $controller, application, securityGroup) {
      $scope.pages = {
        location: require('./createSecurityGroupProperties.html'),
        targets: require('./createSecurityGroupTargets.html'),
        sourceFilters: require('./createSecurityGroupSourceFilters.html'),
        ingress: require('./createSecurityGroupIngress.html'),
      };

      const ctrl = this;

      securityGroup.backingData = {};
      securityGroup.network = 'default';
      securityGroup.sourceRanges = [];
      securityGroup.sourceTags = [];
      securityGroup.ipIngress = [];

      angular.extend(
        this,
        $controller('gceConfigSecurityGroupMixin', {
          $scope: $scope,
          $uibModalInstance: $uibModalInstance,
          application: application,
          securityGroup: securityGroup,
          mode: 'create',
        }),
      );

      AccountService.listAccounts('gce').then(function (accounts) {
        $scope.accounts = accounts;
        ctrl.accountUpdated();
      });

      this.getSecurityGroupRefreshTime = function () {
        return InfrastructureCaches.get('securityGroups').getStats().ageMax;
      };

      ctrl.upsert = function () {
        ctrl.mixinUpsert('Create');
      };

      ctrl.initializeSecurityGroups();
    },
  ]);
