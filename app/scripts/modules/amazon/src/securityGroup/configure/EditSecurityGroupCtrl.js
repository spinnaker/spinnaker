'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import { FirewallLabels, SecurityGroupWriter, TaskMonitor } from '@spinnaker/core';

export const AMAZON_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL = 'spinnaker.amazon.securityGroup.edit.controller';
export const name = AMAZON_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL; // for backwards compatibility
angular
  .module(AMAZON_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL, [UIROUTER_ANGULARJS])
  .controller('awsEditSecurityGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    '$state',
    'application',
    'securityGroup',
    '$controller',
    function ($scope, $uibModalInstance, $state, application, securityGroup, $controller) {
      $scope.self = $scope;
      $scope.pages = {
        ingress: require('./createSecurityGroupIngress.html'),
      };

      $scope.securityGroup = securityGroup;

      $scope.state = {
        refreshingSecurityGroups: false,
      };

      $scope.securityGroup.regions = [$scope.securityGroup.region];
      $scope.securityGroup.credentials = $scope.securityGroup.accountName;

      angular.extend(
        this,
        $controller('awsConfigSecurityGroupMixin', {
          $scope: $scope,
          $uibModalInstance: $uibModalInstance,
          application: application,
          securityGroup: securityGroup,
        }),
      );

      $scope.state.isNew = false;

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: `Updating your ${FirewallLabels.get('firewall')}`,
        modalInstance: $uibModalInstance,
        onTaskComplete: () => application.securityGroups.refresh(),
      });

      securityGroup.securityGroupIngress = _.chain(securityGroup.inboundRules)
        .filter((rule) => rule.securityGroup)
        .map((rule) =>
          rule.portRanges.map((portRange) => {
            const vpcId = rule.securityGroup.vpcId === securityGroup.vpcId ? null : rule.securityGroup.vpcId;
            return {
              accountName: rule.securityGroup.accountName || rule.securityGroup.accountId,
              accountId: rule.securityGroup.accountId,
              vpcId: vpcId,
              id: rule.securityGroup.id,
              name: rule.securityGroup.inferredName ? null : rule.securityGroup.name,
              type: rule.protocol,
              startPort: portRange.startPort,
              endPort: portRange.endPort,
              existing: true,
            };
          }),
        )
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

      this.upsert = function () {
        const group = $scope.securityGroup;
        const command = {
          credentials: group.accountName,
          name: group.name,
          description: group.description,
          vpcId: group.vpcId,
          region: group.region,
          securityGroupIngress: group.securityGroupIngress,
          ipIngress: group.ipIngress,
        };

        $scope.taskMonitor.submit(function () {
          return SecurityGroupWriter.upsertSecurityGroup(command, application, 'Update');
        });
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };

      this.initializeSecurityGroups().then(this.initializeAccounts);
    },
  ]);
