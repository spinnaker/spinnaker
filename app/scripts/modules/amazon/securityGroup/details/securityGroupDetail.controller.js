'use strict';

import _ from 'lodash';
let angular = require('angular');

import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';
import {SECURITY_GROUP_READER} from 'core/securityGroup/securityGroupReader.service';
import {SECURITY_GROUP_WRITER} from 'core/securityGroup/securityGroupWriter.service';

module.exports = angular.module('spinnaker.securityGroup.aws.details.controller', [
  require('angular-ui-router'),
  SECURITY_GROUP_READER,
  SECURITY_GROUP_WRITER,
  CONFIRMATION_MODAL_SERVICE,
  require('../clone/cloneSecurityGroup.controller.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  CLOUD_PROVIDER_REGISTRY,
  require('core/history/recentHistory.service'),
])
  .controller('awsSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, app,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    recentHistoryService, $uibModal, cloudProviderRegistry) {

    const application = app;
    const securityGroup = resolvedSecurityGroup;

    // needed for standalone instances
    $scope.detailsTemplateUrl = cloudProviderRegistry.getValue('aws', 'securityGroup.detailsTemplateUrl');

    $scope.state = {
      loading: true,
      standalone: app.isStandalone,
    };

    function extractSecurityGroup() {
      return securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;

        if (!details || _.isEmpty( details )) {
          fourOhFour();
        } else {
          $scope.securityGroup = details;
          $scope.ipRules = buildIpRulesModel(details);
          $scope.securityGroupRules = buildSecurityGroupRulesModel(details);
        }
      },
        fourOhFour
      );
    }

    function buildIpRulesModel(details) {
      let groupedRangeRules = _.groupBy(details.ipRangeRules, (rule => rule.range.ip + rule.range.cidr));
      return Object.keys(groupedRangeRules)
        .map(addr => {
          return {
            address: addr,
            rules: buildRuleModel(groupedRangeRules, addr),
          };
        })
        .filter(rule => rule.rules.length);
    }

    function buildSecurityGroupRulesModel(details) {
      let groupedRangeRules = _.groupBy(details.securityGroupRules, (rule => rule.securityGroup.id));
      return Object.keys(groupedRangeRules)
        .map(addr => {
          return {
            securityGroup: groupedRangeRules[addr][0].securityGroup,
            rules: buildRuleModel(groupedRangeRules, addr),
          };
        })
        .filter(rule => rule.rules.length);
    }

    function buildRuleModel(groupedRangeRules, addr) {
      let rules = [];
      groupedRangeRules[addr].forEach(rule => {
        (rule.portRanges || []).forEach(range => {
          if (range.startPort !== undefined && range.endPort !== undefined) {
            rules.push({startPort: range.startPort, endPort: range.endPort, protocol: rule.protocol});
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
        recentHistoryService.removeLastItem('securityGroups');
      } else {
        $state.params.allowModalToStayOpen = true;
        $state.go('^', null, {location: 'replace'});
      }
    }

    extractSecurityGroup().then(() => {
      // If the user navigates away from the view before the initial extractSecurityGroup call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed && !app.isStandalone) {
        app.securityGroups.onRefresh($scope, extractSecurityGroup);
      }
    });

    this.editInboundRules = function editInboundRules() {
      $uibModal.open({
        templateUrl: require('../configure/editSecurityGroup.html'),
        controller: 'awsEditSecurityGroupCtrl as ctrl',
        size: 'lg',
        resolve: {
          securityGroup: function() {
            return angular.copy($scope.securityGroup);
          },
          application: function() { return application; }
        }
      });
    };


    this.cloneSecurityGroup = function cloneSecurityGroup() {
      $uibModal.open({
        templateUrl: require('../clone/cloneSecurityGroup.html'),
        controller: 'awsCloneSecurityGroupController as ctrl',
        size: 'lg',
        resolve: {
          securityGroup: function() {
            var securityGroup = angular.copy($scope.securityGroup);
            if(securityGroup.region) {
              securityGroup.regions = [securityGroup.region];
            }
            return securityGroup;
          },
          application: function() { return application; }
        }
      });
    };

    this.deleteSecurityGroup = function deleteSecurityGroup() {
      var taskMonitor = {
        application: application,
        title: 'Deleting ' + securityGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        return securityGroupWriter.deleteSecurityGroup(securityGroup, application, {
          cloudProvider: securityGroup.provider,
          vpcId: securityGroup.vpcId,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + securityGroup.name + '?',
        buttonText: 'Delete ' + securityGroup.name,
        provider: 'aws',
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    if (app.isStandalone) {
      // we still want the edit to refresh the security group details when the modal closes
      app.securityGroups = {
        refresh: extractSecurityGroup
      };
    }
  }
);
