'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  confirmNotManaged,
  FirewallLabels,
  MANAGED_RESOURCE_DETAILS_INDICATOR,
  noop,
  RecentHistoryService,
  SECURITY_GROUP_READER,
  SecurityGroupWriter,
} from '@spinnaker/core';

import { AMAZON_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER } from '../clone/cloneSecurityGroup.controller';
import { VpcReader } from '../../vpc/VpcReader';

export const AMAZON_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER =
  'spinnaker.amazon.securityGroup.details.controller';
export const name = AMAZON_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER; // for backwards compatibility
angular
  .module(AMAZON_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER, [
    UIROUTER_ANGULARJS,
    SECURITY_GROUP_READER,
    AMAZON_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER,
    MANAGED_RESOURCE_DETAILS_INDICATOR,
  ])
  .controller('awsSecurityGroupDetailsCtrl', [
    '$scope',
    '$state',
    'resolvedSecurityGroup',
    'app',
    'securityGroupReader',
    '$uibModal',
    function ($scope, $state, resolvedSecurityGroup, app, securityGroupReader, $uibModal) {
      this.application = app;
      const application = app;
      const securityGroup = resolvedSecurityGroup;
      this.firewallLabel = FirewallLabels.get('Firewall');

      // needed for standalone instances
      $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('aws', 'securityGroup.detailsTemplateUrl');

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
              rules.push({
                startPort: range.startPort,
                endPort: range.endPort,
                protocol: rule.protocol,
                description: rule.description,
              });
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

      function checkManagement() {
        return confirmNotManaged($scope.securityGroup, application);
      }

      this.editInboundRules = function editInboundRules() {
        confirmNotManaged($scope.securityGroup, application).then((notManaged) => {
          notManaged &&
            $uibModal.open({
              templateUrl: require('../configure/editSecurityGroup.html'),
              controller: 'awsEditSecurityGroupCtrl as ctrl',
              size: 'lg',
              resolve: {
                securityGroup: function () {
                  return angular.copy($scope.securityGroup);
                },
                application: function () {
                  return application;
                },
              },
            });
        });
      };

      this.cloneSecurityGroup = function cloneSecurityGroup() {
        $uibModal.open({
          templateUrl: require('../clone/cloneSecurityGroup.html'),
          controller: 'awsCloneSecurityGroupController as ctrl',
          size: 'lg',
          resolve: {
            securityGroup: function () {
              const securityGroup = angular.copy($scope.securityGroup);
              if (securityGroup.region) {
                securityGroup.regions = [securityGroup.region];
              }
              return securityGroup;
            },
            application: function () {
              return application;
            },
          },
        });
      };

      this.deleteSecurityGroup = function deleteSecurityGroup() {
        let isRetry = false;
        const retryParams = { removeDependencies: true };

        const taskMonitor = {
          application: application,
          title: 'Deleting ' + securityGroup.name,
          onTaskRetry: () => {
            isRetry = true;
          },
        };

        const submitMethod = () => {
          const params = {
            cloudProvider: securityGroup.provider,
            vpcId: securityGroup.vpcId,
          };
          if (isRetry) {
            Object.assign(params, retryParams);
          }
          return SecurityGroupWriter.deleteSecurityGroup(securityGroup, application, params);
        };

        confirmNotManaged($scope.securityGroup, application).then((notManaged) => {
          notManaged &&
            ConfirmationModalService.confirm({
              header: 'Really delete ' + securityGroup.name + '?',
              buttonText: 'Delete ' + securityGroup.name,
              account: securityGroup.accountId,
              taskMonitorConfig: taskMonitor,
              submitMethod: submitMethod,
              retryBody: `<div><p>Retry deleting the ${FirewallLabels.get(
                'firewall',
              )} and revoke any dependent ingress rules?</p><p>Any instance or load balancer associations will have to removed manually.</p></div>`,
            });
        });
      };

      if (app.isStandalone) {
        // we still want the edit to refresh the firewall details when the modal closes
        app.securityGroups = {
          refresh: extractSecurityGroup,
        };
      }
    },
  ]);
