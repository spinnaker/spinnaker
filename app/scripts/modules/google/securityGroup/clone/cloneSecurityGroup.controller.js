'use strict';

const angular = require('angular');
import _ from 'lodash';

import { ACCOUNT_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.google.securityGroup.clone.controller', [
    ACCOUNT_SERVICE,
    require('../configure/ConfigSecurityGroupMixin.controller.js')
  ])
  .controller('gceCloneSecurityGroupController', function($scope, $uibModalInstance, $controller, accountService, securityGroup, application) {
    var vm = this;

    $scope.pages = {
      location: require('../configure/createSecurityGroupProperties.html'),
      targets: require('../configure/createSecurityGroupTargets.html'),
      sourceFilters: require('../configure/createSecurityGroupSourceFilters.html'),
      ingress: require('../configure/createSecurityGroupIngress.html'),
    };

    angular.extend(this, $controller('gceConfigSecurityGroupMixin', {
      $scope: $scope,
      $uibModalInstance: $uibModalInstance,
      application: application,
      securityGroup: securityGroup,
      mode: 'clone',
    }));

    accountService.listAccounts('gce').then(function(accounts) {
      $scope.accounts = accounts;
      vm.accountUpdated();
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

    securityGroup.backingData = {};

    securityGroup.sourceTags = securityGroup.sourceTags || [];

    vm.upsert = function () {
      vm.mixinUpsert('Clone');
    };

    vm.initializeSecurityGroups();

  });
