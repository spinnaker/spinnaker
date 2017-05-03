'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';
import {SECURITY_GROUP_WRITER} from 'core/securityGroup/securityGroupWriter.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular.module('spinnaker.google.securityGroup.edit.controller', [
  require('angular-ui-router').default,
  ACCOUNT_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  TASK_MONITOR_BUILDER,
  SECURITY_GROUP_WRITER
])
  .controller('gceEditSecurityGroupCtrl', function($scope, $uibModalInstance, $state,
                                                   accountService,
                                                   taskMonitorBuilder, infrastructureCaches,
                                                   application, securityGroup, securityGroupWriter, $controller) {

    $scope.pages = {
      targets: require('./createSecurityGroupTargets.html'),
      sourceFilters: require('./createSecurityGroupSourceFilters.html'),
      ingress: require('./createSecurityGroupIngress.html'),
    };

    $scope.securityGroup = securityGroup;

    $scope.state = {
      refreshingSecurityGroups: false,
    };

    angular.extend(this, $controller('gceConfigSecurityGroupMixin', {
      $scope: $scope,
      $uibModalInstance: $uibModalInstance,
      application: application,
      securityGroup: securityGroup,
      mode: 'edit',
    }));

    $scope.isNew = false;

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Updating your security group',
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.securityGroups.refresh(),
    });

    securityGroup.sourceRanges = _.map(securityGroup.sourceRanges, function(sourceRange) {
      return {value: sourceRange};
    });

    securityGroup.ipIngress = _.chain(securityGroup.ipIngressRules)
      .map(function(rule) {
        if (rule.portRanges && rule.portRanges.length > 0) {
          return rule.portRanges.map(function (portRange) {
            return {
              type: rule.protocol,
              startPort: portRange.startPort,
              endPort: portRange.endPort,
            };
          });
        } else {
          return [{
            type: rule.protocol,
          }];
        }
      })
      .flatten()
      .value();

    securityGroup.sourceTags = securityGroup.sourceTags || [];

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.get('securityGroups').getStats().ageMax;
    };

    this.addSourceCIDR = function(sourceRanges) {
      sourceRanges.push({value: '0.0.0.0/0'});
    };

    this.removeSourceCIDR = function(sourceRanges, index) {
      sourceRanges.splice(index, 1);
    };

    this.addRule = function(ruleset) {
      ruleset.push({
        type: 'tcp',
        startPort: 7001,
        endPort: 7001,
      });
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    this.upsert = function () {
      $scope.taskMonitor.submit(
        function() {
          var allowed = _.map($scope.securityGroup.ipIngress, function(ipIngressRule) {
            var rule = {
              ipProtocol: ipIngressRule.type,
            };

            if (ipIngressRule.startPort && ipIngressRule.endPort) {
              rule.portRanges = [ipIngressRule.startPort + '-' + ipIngressRule.endPort];
            }

            return rule;
          });

          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Update', {
            cloudProvider: 'gce',
            securityGroupName: $scope.securityGroup.name,
            sourceRanges: _.uniq(_.map($scope.securityGroup.sourceRanges, 'value')),
            allowed: allowed,
            targetTags: $scope.securityGroup.targetTags || [],
            region: 'global',
          });
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
