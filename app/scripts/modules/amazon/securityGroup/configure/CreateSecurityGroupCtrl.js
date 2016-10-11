'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.amazon.securityGroup.create.controller', [
  require('angular-ui-router'),
  require('core/cache/infrastructureCaches.js'),
  require('core/cache/cacheInitializer.js'),
  require('core/task/monitor/taskMonitorService.js'),
  require('core/securityGroup/securityGroup.read.service.js'),
  require('core/config/settings.js'),
])
  .controller('awsCreateSecurityGroupCtrl', function($scope, $uibModalInstance, $state, $controller,
                                                  securityGroupReader,
                                                  taskMonitorService, cacheInitializer, infrastructureCaches,
                                                  application, securityGroup, settings ) {

    $scope.pages = {
      location: require('./createSecurityGroupProperties.html'),
      ingress: require('./createSecurityGroupIngress.html'),
    };

    var ctrl = this;

    angular.extend(this, $controller('awsConfigSecurityGroupMixin', {
      $scope: $scope,
      $uibModalInstance: $uibModalInstance,
      application: application,
      securityGroup: securityGroup,
      settings: settings,
    }));

    $scope.state.isNew = true;

    ctrl.upsert = () => ctrl.mixinUpsert('Create');

    ctrl.initializeSecurityGroups().then(ctrl.initializeAccounts);

  });
