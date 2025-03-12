'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import { VpcReader } from '@spinnaker/amazon';
import {
  CloudProviderRegistry,
  FirewallLabels,
  MANAGED_RESOURCE_DETAILS_INDICATOR,
  RecentHistoryService,
  SECURITY_GROUP_READER,
} from '@spinnaker/core';

export const ECS_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER =
  'spinnaker.ecs.securityGroup.details.controller';
export const name = ECS_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER; // for backwards compatibility
angular
  .module(ECS_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER, [
    UIROUTER_ANGULARJS,
    SECURITY_GROUP_READER,
    MANAGED_RESOURCE_DETAILS_INDICATOR,
  ])
  .controller('ecsSecurityGroupDetailsCtrl', [
    '$scope',
    '$state',
    'resolvedSecurityGroup',
    'app',
    'securityGroupReader',
    function ($scope, $state, resolvedSecurityGroup, app, securityGroupReader) {
      this.application = app;
      const application = app;
      const securityGroup = resolvedSecurityGroup;
      this.firewallLabel = FirewallLabels.get('Firewall');

      // needed for standalone instances
      $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('ecs', 'securityGroup.detailsTemplateUrl');

      $scope.state = {
        loading: true,
        standalone: app.isStandalone,
      };

      function extractSecurityGroup() {
        return securityGroupReader
          .getSecurityGroupDetails(
            application,
            securityGroup.accountId,
            securityGroup.provider,
            securityGroup.region,
            securityGroup.vpcId,
            securityGroup.name,
          )
          .then(function (details) {
            return VpcReader.getVpcName(details.vpcId).then((name) => {
              details.vpcName = name;
              return details;
            });
          })
          .then(function (details) {
            $scope.state.loading = false;

            if (!details || _.isEmpty(details)) {
              fourOhFour();
            } else {
              const applicationSecurityGroup = securityGroupReader.getApplicationSecurityGroup(
                application,
                securityGroup.accountId,
                securityGroup.region,
                securityGroup.name,
              );

              angular.extend(securityGroup, applicationSecurityGroup, details);
              $scope.securityGroup = securityGroup;
              $scope.ipRules = buildIpRulesModel(securityGroup);
              $scope.securityGroupRules = buildSecurityGroupRulesModel(securityGroup);
            }
          }, fourOhFour);
      }

      function buildIpRulesModel(details) {
        const groupedRangeRules = _.groupBy(details.ipRangeRules, (rule) => rule.range.ip + rule.range.cidr);
        return Object.keys(groupedRangeRules)
          .map((addr) => {
            return {
              address: addr,
              rules: buildRuleModel(groupedRangeRules, addr),
            };
          })
          .filter((rule) => rule.rules.length);
      }

      function buildSecurityGroupRulesModel(details) {
        const groupedRangeRules = _.groupBy(details.securityGroupRules, (rule) => rule.securityGroup.id);
        return Object.keys(groupedRangeRules)
          .map((addr) => {
            return {
              securityGroup: groupedRangeRules[addr][0].securityGroup,
              rules: buildRuleModel(groupedRangeRules, addr),
            };
          })
          .filter((rule) => rule.rules.length);
      }

      function buildRuleModel(groupedRangeRules, addr) {
        const rules = [];
        groupedRangeRules[addr].forEach((rule) => {
          (rule.portRanges || []).forEach((range) => {
            if (rule.protocol === '-1' || (range.startPort !== undefined && range.endPort !== undefined)) {
              rules.push({ startPort: range.startPort, endPort: range.endPort, protocol: rule.protocol });
            }
          });
        });
        return rules;
      }

      function fourOhFour() {
        if ($scope.$$destroyed) {
          return;
        }
        if (app.isStandalone) {
          $scope.group = securityGroup.name;
          $scope.state.notFound = true;
          $scope.state.loading = false;
          RecentHistoryService.removeLastItem('securityGroups');
        } else {
          $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
        }
      }

      extractSecurityGroup().then(() => {
        // If the user navigates away from the view before the initial extractSecurityGroup call completes,
        // do not bother subscribing to the refresh
        if (!$scope.$$destroyed && !app.isStandalone) {
          app.securityGroups.onRefresh($scope, extractSecurityGroup);
        }
      });

      if (app.isStandalone) {
        // we still want the edit to refresh the firewall details when the modal closes
        app.securityGroups = {
          refresh: extractSecurityGroup,
        };
      }
    },
  ]);
