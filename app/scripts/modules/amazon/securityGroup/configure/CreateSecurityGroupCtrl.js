'use strict';

let angular = require('angular');
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';
import {CACHE_INITIALIZER_SERVICE} from 'core/cache/cacheInitializer.service';

module.exports = angular.module('spinnaker.amazon.securityGroup.create.controller', [
  require('angular-ui-router'),
  INFRASTRUCTURE_CACHE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  require('core/task/monitor/taskMonitorService.js'),
  require('core/config/settings.js'),
])
  .controller('awsCreateSecurityGroupCtrl', function($scope, $uibModalInstance, $state, $controller,
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
