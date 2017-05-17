'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, INFRASTRUCTURE_CACHE_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.gce.securityGroup.create.controller', [
  require('angular-ui-router').default,
  ACCOUNT_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
])
  .controller('gceCreateSecurityGroupCtrl', function($scope, $uibModalInstance, $state, $controller,
                                                     accountService, infrastructureCaches, application, securityGroup ) {

    $scope.pages = {
      location: require('./createSecurityGroupProperties.html'),
      targets: require('./createSecurityGroupTargets.html'),
      sourceFilters: require('./createSecurityGroupSourceFilters.html'),
      ingress: require('./createSecurityGroupIngress.html'),
    };

    var ctrl = this;

    securityGroup.backingData = {};
    securityGroup.network = 'default';
    securityGroup.sourceRanges = [];
    securityGroup.sourceTags = [];
    securityGroup.ipIngress = [];

    angular.extend(this, $controller('gceConfigSecurityGroupMixin', {
      $scope: $scope,
      $uibModalInstance: $uibModalInstance,
      application: application,
      securityGroup: securityGroup,
      mode: 'create',
    }));


    accountService.listAccounts('gce').then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.get('securityGroups').getStats().ageMax;
    };


    ctrl.upsert = function () {
      ctrl.mixinUpsert('Create');
    };

    ctrl.initializeSecurityGroups();

  });
