'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AccountService, FirewallLabels } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.amazon.securityGroup.clone.controller', [
    require('../configure/configSecurityGroup.mixin.controller').name,
  ])
  .controller('awsCloneSecurityGroupController', function(
    $scope,
    $uibModalInstance,
    $controller,
    securityGroup,
    application,
  ) {
    var vm = this;

    vm.firewallLabel = FirewallLabels.get('Firewall');

    $scope.pages = {
      location: require('../configure/createSecurityGroupProperties.html'),
      ingress: require('../configure/createSecurityGroupIngress.html'),
    };

    securityGroup.credentials = securityGroup.accountName;
    $scope.namePreview = securityGroup.name;

    angular.extend(
      this,
      $controller('awsConfigSecurityGroupMixin', {
        $scope: $scope,
        $uibModalInstance: $uibModalInstance,
        application: application,
        securityGroup: securityGroup,
      }),
    );

    // We want to let people clone as a means to copy security groups across
    // regions so don't block them because the names already exist.
    $scope.allowDuplicateNames = true;

    AccountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
      vm.accountUpdated();
    });

    securityGroup.securityGroupIngress = _.chain(securityGroup.inboundRules)
      .filter(function(rule) {
        return rule.securityGroup;
      })
      .map(function(rule) {
        return rule.portRanges.map(function(portRange) {
          return {
            name: rule.securityGroup.name,
            type: rule.protocol,
            startPort: portRange.startPort,
            endPort: portRange.endPort,
          };
        });
      })
      .flatten()
      .value();

    securityGroup.ipIngress = _.chain(securityGroup.inboundRules)
      .filter(function(rule) {
        return rule.range;
      })
      .map(function(rule) {
        return rule.portRanges.map(function(portRange) {
          return {
            cidr: rule.range.ip + rule.range.cidr,
            type: rule.protocol,
            startPort: portRange.startPort,
            endPort: portRange.endPort,
          };
        });
      })
      .flatten()
      .value();

    vm.upsert = function() {
      // <account-select-field> only updates securityGroup.credentials, but Orca looks at account* before looking at credentials
      // Updating the rest of the attributes to send the correct (expected) account for all attributes
      const { credentials } = $scope.securityGroup;
      Object.assign($scope.securityGroup, {
        account: credentials,
        accountName: credentials,
        accountId: credentials,
      });

      vm.mixinUpsert('Clone');
    };

    vm.initializeSecurityGroups();
  });
