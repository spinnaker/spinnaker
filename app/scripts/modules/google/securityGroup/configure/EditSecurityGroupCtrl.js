'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.google.securityGroup.edit.controller', [
  require('angular-ui-router'),
  require('../../../core/account/account.service.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/task/monitor/taskMonitorService.js'),
  require('../../../core/securityGroup/securityGroup.write.service.js'),
])
  .controller('gceEditSecurityGroupCtrl', function($scope, $modalInstance, $state,
                                                   accountService,  securityGroupReader,
                                                   taskMonitorService, cacheInitializer, infrastructureCaches,
                                                   _, application, securityGroup, securityGroupWriter) {

    $scope.pages = {
      ingress: require('./createSecurityGroupIngress.html'),
    };

    $scope.securityGroup = securityGroup;

    $scope.state = {
      refreshingSecurityGroups: false,
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Updating your security group',
      forceRefreshMessage: 'Getting your updated security group from Google...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    securityGroup.sourceRanges = _.map(securityGroup.sourceRanges, function(sourceRange) {
      return {value: sourceRange};
    });

    securityGroup.ipIngress = _(securityGroup.ipIngressRules)
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

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.securityGroups.getStats().ageMax;
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

    $scope.taskMonitor.onApplicationRefresh = $modalInstance.dismiss;

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
            providerType: 'gce',
            securityGroupName: $scope.securityGroup.name,
            sourceRanges: _.uniq(_.pluck($scope.securityGroup.sourceRanges, 'value')),
            allowed: allowed,
            targetTags: $scope.securityGroup.targetTags || [],
            region: "global",
          });
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
