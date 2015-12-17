'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.aws.edit.controller', [
  require('angular-ui-router'),
  require('../../../core/account/account.service.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/task/monitor/taskMonitorService.js'),
  require('../../../core/securityGroup/securityGroup.write.service.js'),
])
  .controller('awsEditSecurityGroupCtrl', function($scope, $modalInstance, $state,
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
      forceRefreshMessage: 'Getting your updated security group from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    securityGroup.securityGroupIngress = _(securityGroup.inboundRules)
      .filter(function(rule) {
        return rule.securityGroup;
      }).map(function(rule) {
        return rule.portRanges.map(function(portRange) {
          return {
            name: rule.securityGroup.name,
            type: rule.protocol,
            startPort: portRange.startPort,
            endPort: portRange.endPort
          };
        });
      })
      .flatten()
      .value();

    securityGroup.ipIngress = _(securityGroup.inboundRules)
      .filter(function(rule) {
        return rule.range;
      }).map(function(rule) {
        return rule.portRanges.map(function(portRange) {
          return {
            cidr: rule.range.ip + rule.range.cidr,
            type: rule.protocol,
            startPort: portRange.startPort,
            endPort: portRange.endPort
          };
        });
      })
      .flatten()
      .value();

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    this.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        initializeSecurityGroups().then(function() {
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };



    function initializeSecurityGroups() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        var account = securityGroup.accountName,
          region = securityGroup.region,
          vpcId = securityGroup.vpcId || null,
          availableGroups = _.filter(securityGroups[account].aws[region], { vpcId: vpcId });
        $scope.availableSecurityGroups = _.pluck(availableGroups, 'name');
      });
    }

    this.addRule = function(ruleset) {
      ruleset.push({});
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    $scope.taskMonitor.onApplicationRefresh = $modalInstance.dismiss;

    this.upsert = function () {

      $scope.taskMonitor.submit(
        function() {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Update');
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };

    initializeSecurityGroups();
  });
