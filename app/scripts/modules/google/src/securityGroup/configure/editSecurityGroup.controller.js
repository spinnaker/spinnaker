'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';

import { FirewallLabels, InfrastructureCaches, SecurityGroupWriter, TaskMonitor } from '@spinnaker/core';

export const GOOGLE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUP_CONTROLLER =
  'spinnaker.google.securityGroup.edit.controller';
export const name = GOOGLE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUP_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUP_CONTROLLER, [UIROUTER_ANGULARJS])
  .controller('gceEditSecurityGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    '$state',
    'application',
    'securityGroup',
    '$controller',
    function ($scope, $uibModalInstance, $state, application, securityGroup, $controller) {
      $scope.pages = {
        targets: require('./createSecurityGroupTargets.html'),
        sourceFilters: require('./createSecurityGroupSourceFilters.html'),
        ingress: require('./createSecurityGroupIngress.html'),
      };

      $scope.securityGroup = securityGroup;

      $scope.state = {
        refreshingSecurityGroups: false,
      };

      angular.extend(
        this,
        $controller('gceConfigSecurityGroupMixin', {
          $scope: $scope,
          $uibModalInstance: $uibModalInstance,
          application: application,
          securityGroup: securityGroup,
          mode: 'edit',
        }),
      );

      $scope.isNew = false;

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: `Updating your ${FirewallLabels.get('firewall')}`,
        modalInstance: $uibModalInstance,
        onTaskComplete: () => application.securityGroups.refresh(),
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

      securityGroup.sourceTags = securityGroup.sourceTags || [];

      this.getSecurityGroupRefreshTime = function () {
        return InfrastructureCaches.get('securityGroups').getStats().ageMax;
      };

      this.addSourceCIDR = function (sourceRanges) {
        sourceRanges.push({ value: '0.0.0.0/0' });
      };

      this.removeSourceCIDR = function (sourceRanges, index) {
        sourceRanges.splice(index, 1);
      };

      this.addRule = function (ruleset) {
        ruleset.push({
          type: 'tcp',
          startPort: 7001,
          endPort: 7001,
        });
      };

      this.removeRule = function (ruleset, index) {
        ruleset.splice(index, 1);
      };

      this.upsert = function () {
        $scope.taskMonitor.submit(function () {
          const allowed = _.map($scope.securityGroup.ipIngress, function (ipIngressRule) {
            const rule = {
              ipProtocol: ipIngressRule.type,
            };

            if (ipIngressRule.startPort && ipIngressRule.endPort) {
              rule.portRanges = [ipIngressRule.startPort + '-' + ipIngressRule.endPort];
            }

            return rule;
          });

          return SecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Update', {
            cloudProvider: 'gce',
            sourceRanges: _.uniq(_.map($scope.securityGroup.sourceRanges, 'value')),
            allowed: allowed,
            targetTags: $scope.securityGroup.targetTags || [],
            region: 'global',
          });
        });
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };
    },
  ]);
