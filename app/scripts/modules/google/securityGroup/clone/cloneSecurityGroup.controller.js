'use strict';

var angular = require('angular');

module.exports = angular
  .module('spinnaker.google.securityGroup.clone.controller', [
    require('../../../core/account/account.service.js'),
    require('../../../core/task/monitor/taskMonitorService.js'),
    require('../../../core/securityGroup/securityGroup.write.service.js'),
    require('../../../core/utils/lodash.js'),
    require('../configure/ConfigSecurityGroupMixin.controller.js')
  ])
  .controller('gceCloneSecurityGroupController', function($scope, $modalInstance, $controller, taskMonitorService, accountService, securityGroupWriter, securityGroup, application, _) {
    var vm = this;

    $scope.pages = {
      location: require('../configure/createSecurityGroupProperties.html'),
      ingress: require('../configure/createSecurityGroupIngress.html'),
    };

    angular.extend(this, $controller('gceConfigSecurityGroupMixin', {
      $scope: $scope,
      $modalInstance: $modalInstance,
      application: application,
      securityGroup: securityGroup,
    }));

    accountService.listAccounts('gce').then(function(accounts) {
      $scope.accounts = accounts;
      vm.accountUpdated();
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

    securityGroup.backingData = {};

    delete securityGroup.targetTags;

    vm.upsert = function () {
      vm.mixinUpsert('Clone');
    };

    vm.initializeSecurityGroups();

  });
