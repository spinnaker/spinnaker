'use strict';

const angular = require('angular');

import { CACHE_INITIALIZER_SERVICE, INFRASTRUCTURE_CACHE_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.amazon.securityGroup.create.controller', [
  require('angular-ui-router').default,
  INFRASTRUCTURE_CACHE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
])
  .controller('awsCreateSecurityGroupCtrl', function($scope, $uibModalInstance, $state, $controller,
                                                     cacheInitializer, infrastructureCaches,
                                                     application, securityGroup) {

    $scope.pages = {
      location: require('./createSecurityGroupProperties.html'),
      ingress: require('./createSecurityGroupIngress.html'),
    };

    var ctrl = this;

    angular.extend(this, $controller('awsConfigSecurityGroupMixin', {
      $scope: $scope,
      $uibModalInstance: $uibModalInstance,
      infrastructureCaches: infrastructureCaches,
      application: application,
      securityGroup: securityGroup
    }));

    $scope.state.isNew = true;

    ctrl.upsert = () => ctrl.mixinUpsert('Create');

    ctrl.initializeSecurityGroups().then(ctrl.initializeAccounts);

  });
