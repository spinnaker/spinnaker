'use strict';

var angular = require('angular');

module.exports = angular
  .module('spinnaker.securityGroup.clone.controller', [
    'spinnaker.account.service',
    'spinnaker.tasks.monitor.service',
    'spinnaker.securityGroup.write.service',
    'spinnaker.vpc.read.service',
    require('utils/lodash.js'),
    require('../../configure/aws/ConfigSecurityGroupMixin.controller.js')
  ])
  .controller('CloneSecurityGroupController', function($scope, $modalInstance, $controller, taskMonitorService, accountService, securityGroupWriter, vpcReader, securityGroup, application, _) {
    var vm = this;


    angular.extend(this, $controller('ConfigSecurityGroupMixin', {
      $scope: $scope,
      $modalInstance: $modalInstance,
      application: application,
      securityGroup: securityGroup,
    }));


    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
      vm.accountUpdated();
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


    vm.upsert = function () {
      vm.mixinUpsert('Clone');
    };

    vm.initializeSecurityGroups();

  }).name;
