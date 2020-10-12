'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { AccountService, FirewallLabels } from '@spinnaker/core';
import { GOOGLE_SECURITYGROUP_CONFIGURE_CONFIGSECURITYGROUPMIXIN_CONTROLLER } from '../configure/ConfigSecurityGroupMixin.controller';

export const GOOGLE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER =
  'spinnaker.google.securityGroup.clone.controller';
export const name = GOOGLE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER, [
    GOOGLE_SECURITYGROUP_CONFIGURE_CONFIGSECURITYGROUPMIXIN_CONTROLLER,
  ])
  .controller('gceCloneSecurityGroupController', [
    '$scope',
    '$uibModalInstance',
    '$controller',
    'securityGroup',
    'application',
    function ($scope, $uibModalInstance, $controller, securityGroup, application) {
      const vm = this;

      $scope.pages = {
        location: require('../configure/createSecurityGroupProperties.html'),
        targets: require('../configure/createSecurityGroupTargets.html'),
        sourceFilters: require('../configure/createSecurityGroupSourceFilters.html'),
        ingress: require('../configure/createSecurityGroupIngress.html'),
      };

      $scope.firewallLabel = FirewallLabels.get('Firewall');

      angular.extend(
        this,
        $controller('gceConfigSecurityGroupMixin', {
          $scope: $scope,
          $uibModalInstance: $uibModalInstance,
          application: application,
          securityGroup: securityGroup,
          mode: 'clone',
        }),
      );

      AccountService.listAccounts('gce').then(function (accounts) {
        $scope.accounts = accounts;
        vm.accountUpdated();
      });

      securityGroup.sourceRanges = _.map(securityGroup.sourceRanges, function (sourceRange) {
        return { value: sourceRange };
      });

      securityGroup.ipIngress = _.chain(securityGroup.ipIngressRules)
        .map(function (rule) {
          if (rule.portRanges && rule.portRanges.length > 0) {
            return rule.portRanges.map(function (portRange) {
              return {
                type: rule.protocol,
                startPort: portRange.startPort,
                endPort: portRange.endPort,
              };
            });
          } else {
            return [
              {
                type: rule.protocol,
              },
            ];
          }
        })
        .flatten()
        .value();

      securityGroup.backingData = {};

      securityGroup.sourceTags = securityGroup.sourceTags || [];

      vm.upsert = function () {
        vm.mixinUpsert('Clone');
      };

      vm.initializeSecurityGroups();
    },
  ]);
