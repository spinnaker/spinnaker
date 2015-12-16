'use strict';

var angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.securityGroup.clone.controller', [
    require('../../../core/account/account.service.js'),
    require('../../../core/task/monitor/taskMonitorService.js'),
    require('../../../core/securityGroup/securityGroup.write.service.js'),
    require('../../../amazon/vpc/vpc.read.service.js'),
    require('../../../core/utils/lodash.js'),
    require('../configure/configSecurityGroup.mixin.controller.js')
  ])
  .controller('azureCloneSecurityGroupController', function($scope, $modalInstance, $controller, taskMonitorService, accountService, securityGroupWriter, vpcReader, securityGroup, application, _) {
    var vm = this;

    $scope.pages = {
      location: require('../configure/createSecurityGroupProperties.html'),
      ingress: require('../configure/createSecurityGroupIngress.html'),
    };

    angular.extend(this, $controller('azureConfigSecurityGroupMixin', {
      $scope: $scope,
      $modalInstance: $modalInstance,
      application: application,
      securityGroup: securityGroup,
    }));


    accountService.listAccounts('azure').then(function(accounts) {
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

  });
