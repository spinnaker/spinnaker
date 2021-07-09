'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import {
  AccountService,
  CloudProviderRegistry,
  ConfirmationModalService,
  FirewallLabels,
  SECURITY_GROUP_READER,
  SecurityGroupWriter,
} from '@spinnaker/core';

import { GOOGLE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER } from '../clone/cloneSecurityGroup.controller';
import { GCE_SECURITY_GROUP_HELP_TEXT_SERVICE } from '../securityGroupHelpText.service';

export const GOOGLE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER =
  'spinnaker.securityGroup.gce.details.controller';
export const name = GOOGLE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER, [
    UIROUTER_ANGULARJS,
    SECURITY_GROUP_READER,
    GOOGLE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER,
    GCE_SECURITY_GROUP_HELP_TEXT_SERVICE,
  ])
  .controller('gceSecurityGroupDetailsCtrl', [
    '$scope',
    '$state',
    'resolvedSecurityGroup',
    'app',
    'securityGroupReader',
    '$uibModal',
    'gceSecurityGroupHelpTextService',
    function (
      $scope,
      $state,
      resolvedSecurityGroup,
      app,
      securityGroupReader,
      $uibModal,
      gceSecurityGroupHelpTextService,
    ) {
      const application = (this.application = app);
      const securityGroup = resolvedSecurityGroup;

      // needed for standalone instances
      $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('gce', 'securityGroup.detailsTemplateUrl');

      $scope.state = {
        loading: true,
        standalone: app.isStandalone,
      };

      $scope.firewallLabel = FirewallLabels.get('Firewall');

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
            $scope.state.loading = false;

            if (!details || _.isEmpty(details)) {
              fourOhFour();
            } else {
              $scope.securityGroup = details;
              const applicationSecurityGroup = securityGroupReader.getApplicationSecurityGroup(
                application,
                securityGroup.accountId,
                securityGroup.region,
                securityGroup.name,
              );
              $scope.securityGroup = angular.extend(_.cloneDeep(applicationSecurityGroup), $scope.securityGroup);

              // These come back from the global firewall endpoint as '[tag-a, tag-b]'
              if (typeof $scope.securityGroup.targetTags === 'string') {
                const targetTags = $scope.securityGroup.targetTags;
                $scope.securityGroup.targetTags = targetTags.substring(1, targetTags.length - 1).split(', ');
              }
              if (typeof $scope.securityGroup.sourceTags === 'string') {
                const sourceTags = $scope.securityGroup.sourceTags;
                $scope.securityGroup.sourceTags = sourceTags.substring(1, sourceTags.length - 1).split(', ');
              }

              // These come back from the global firewall endpoint as '[account-a@project.iam.gserviceaccount.com, account-b@project.iam.gserviceaccount.com]'
              if (typeof $scope.securityGroup.targetServiceAccounts === 'string') {
                const targetServiceAccounts = $scope.securityGroup.targetServiceAccounts;
                $scope.securityGroup.targetServiceAccounts = targetServiceAccounts
                  .substring(1, targetServiceAccounts.length - 1)
                  .split(', ');
              }
              if (typeof $scope.securityGroup.sourceServiceAccounts === 'string') {
                const sourceServiceAccounts = $scope.securityGroup.sourceServiceAccounts;
                $scope.securityGroup.sourceServiceAccounts = sourceServiceAccounts
                  .substring(1, sourceServiceAccounts.length - 1)
                  .split(', ');
              }

              $scope.securityGroup.sourceRanges = _.chain($scope.securityGroup.ipRangeRules)
                .map((rule) => {
                  return rule.range.ip && rule.range.cidr ? rule.range.ip + rule.range.cidr : null;
                })
                .compact()
                .uniq()
                .value();

              const ipIngress = _.map($scope.securityGroup.ipRangeRules, function (ipRangeRule) {
                return {
                  protocol: ipRangeRule.protocol,
                  portRanges: ipRangeRule.portRanges,
                };
              });

              let ipIngressRules = {};

              ipIngress.forEach(function (ipIngressRule) {
                if (_.has(ipIngressRules, ipIngressRule.protocol)) {
                  ipIngressRules[ipIngressRule.protocol] = ipIngressRules[ipIngressRule.protocol].concat(
                    ipIngressRule.portRanges,
                  );

                  ipIngressRules[ipIngressRule.protocol] = _.uniqBy(ipIngressRules[ipIngressRule.protocol], function (
                    portRange,
                  ) {
                    return portRange.startPort + '->' + portRange.endPort;
                  });
                } else {
                  ipIngressRules[ipIngressRule.protocol] = ipIngressRule.portRanges;
                }
              });

              ipIngressRules = _.map(ipIngressRules, function (portRanges, protocol) {
                return {
                  protocol: protocol,
                  portRanges: portRanges,
                };
              });

              $scope.securityGroup.ipIngressRules = ipIngressRules;

              $scope.securityGroup.protocolPortRangeCount = _.sumBy(ipIngressRules, function (ipIngressRule) {
                return ipIngressRule.portRanges.length > 1 ? ipIngressRule.portRanges.length : 1;
              });

              AccountService.getAccountDetails(securityGroup.accountId).then(function (accountDetails) {
                $scope.securityGroup.logsLink =
                  'https://console.developers.google.com/project/' +
                  accountDetails.project +
                  '/logs?service=gce_firewall_rule&minLogLevel=0&filters=text:' +
                  securityGroup.name;
              });

              gceSecurityGroupHelpTextService.register(
                application,
                $scope.securityGroup.accountName,
                $scope.securityGroup.network,
              );
            }
          }, fourOhFour);
      }

      function fourOhFour() {
        if ($scope.$$destroyed) {
          return;
        }
        $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
      }

      application.securityGroups
        .ready()
        .then(() => extractSecurityGroup())
        .then(() => {
          // If the user navigates away from the view before the initial extractSecurityGroup call completes,
          // do not bother subscribing to the refresh
          if (!$scope.$$destroyed && !app.isStandalone) {
            app.securityGroups.onRefresh($scope, extractSecurityGroup);
          }
        });

      this.getTagHelpText = function (tag, tagType) {
        return gceSecurityGroupHelpTextService.getHelpTextForTag(tag, tagType);
      };

      this.editInboundRules = function editInboundRules() {
        $uibModal.open({
          templateUrl: require('../configure/editSecurityGroup.html'),
          controller: 'gceEditSecurityGroupCtrl as ctrl',
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
      };

      this.cloneSecurityGroup = function cloneSecurityGroup() {
        $uibModal.open({
          templateUrl: require('../clone/cloneSecurityGroup.html'),
          controller: 'gceCloneSecurityGroupController as ctrl',
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
        const taskMonitor = {
          application: application,
          title: 'Deleting ' + securityGroup.name,
        };

        const submitMethod = function () {
          return SecurityGroupWriter.deleteSecurityGroup(securityGroup, application, {
            cloudProvider: $scope.securityGroup.type,
            securityGroupName: securityGroup.name,
          });
        };

        ConfirmationModalService.confirm({
          header: 'Really delete ' + securityGroup.name + '?',
          buttonText: 'Delete ' + securityGroup.name,
          account: securityGroup.accountId,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
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
