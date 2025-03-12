'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { AccountService, FirewallLabels } from '@spinnaker/core';
import { AMAZON_SECURITYGROUP_CONFIGURE_CONFIGSECURITYGROUP_MIXIN_CONTROLLER } from '../configure/configSecurityGroup.mixin.controller';

export const AMAZON_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER =
  'spinnaker.amazon.securityGroup.clone.controller';
export const name = AMAZON_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER; // for backwards compatibility
angular
  .module(AMAZON_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER, [
    AMAZON_SECURITYGROUP_CONFIGURE_CONFIGSECURITYGROUP_MIXIN_CONTROLLER,
  ])
  .controller('awsCloneSecurityGroupController', [
    '$scope',
    '$uibModalInstance',
    '$controller',
    'securityGroup',
    'application',
    function ($scope, $uibModalInstance, $controller, securityGroup, application) {
      const vm = this;

      vm.firewallLabel = FirewallLabels.get('Firewall');

      $scope.pages = {
        location: require('../configure/createSecurityGroupProperties.html'),
        ingress: require('../configure/createSecurityGroupIngress.html'),
      };

      securityGroup.credentials = securityGroup.accountName;
      $scope.namePreview = securityGroup.name;

      angular.extend(
        this,
        $controller('awsConfigSecurityGroupMixin', {
          $scope: $scope,
          $uibModalInstance: $uibModalInstance,
          application: application,
          securityGroup: securityGroup,
        }),
      );
      $scope.state.isNew = true;
      // We want to let people clone as a means to copy security groups across
      // regions so don't block them because the names already exist.
      $scope.allowDuplicateNames = true;
      // Used to prevent cloning into an the exiting region
      $scope.state.isClone = true;
      $scope.state.originRegion = securityGroup.regions && securityGroup.regions[0];

      AccountService.listAccounts('aws').then(function (accounts) {
        $scope.accounts = accounts;
        vm.accountUpdated();
      });

      securityGroup.securityGroupIngress = _.chain(securityGroup.inboundRules)
        .filter(function (rule) {
          return rule.securityGroup;
        })
        .map(function (rule) {
          return rule.portRanges.map(function (portRange) {
            return {
              name: rule.securityGroup.name,
              type: rule.protocol,
              startPort: portRange.startPort,
              endPort: portRange.endPort,
            };
          });
        })
        .flatten()
        .value();

      securityGroup.ipIngress = _.chain(securityGroup.inboundRules)
        .filter(function (rule) {
          return rule.range;
        })
        .map(function (rule) {
          return rule.portRanges.map(function (portRange) {
            return {
              cidr: rule.range.ip + rule.range.cidr,
              type: rule.protocol,
              startPort: portRange.startPort,
              endPort: portRange.endPort,
            };
          });
        })
        .flatten()
        .value();

      vm.upsert = function () {
        // <account-select-field> only updates securityGroup.credentials, but Orca looks at account* before looking at credentials
        // Updating the rest of the attributes to send the correct (expected) account for all attributes
        const { credentials } = $scope.securityGroup;
        Object.assign($scope.securityGroup, {
          account: credentials,
          accountName: credentials,
          accountId: credentials,
        });

        vm.mixinUpsert('Clone');
      };

      vm.initializeSecurityGroups().then(vm.initializeAccounts);
    },
  ]);
