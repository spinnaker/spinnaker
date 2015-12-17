'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.amazon.securityGroup.create.controller', [
  require('angular-ui-router'),
  require('../../../core/account/account.service.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/task/monitor/taskMonitorService.js'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
])
  .controller('awsCreateSecurityGroupCtrl', function($scope, $modalInstance, $state, $controller,
                                                  accountService, securityGroupReader,
                                                  taskMonitorService, cacheInitializer, infrastructureCaches,
                                                  _, application, securityGroup ) {

    $scope.pages = {
      location: require('./createSecurityGroupProperties.html'),
      ingress: require('./createSecurityGroupIngress.html'),
    };

    var ctrl = this;

    angular.extend(this, $controller('awsConfigSecurityGroupMixin', {
      $scope: $scope,
      $modalInstance: $modalInstance,
      application: application,
      securityGroup: securityGroup,
    }));


    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };


    ctrl.upsert = function () {
      ctrl.mixinUpsert('Create');
    };

    ctrl.initializeSecurityGroups();

  });
