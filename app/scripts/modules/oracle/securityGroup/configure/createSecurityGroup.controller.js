'use strict';

const angular = require('angular');

import {
  INFRASTRUCTURE_CACHE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.oraclebmcs.securityGroup.create.controller', [
  INFRASTRUCTURE_CACHE_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  require('@uirouter/angularjs').default,
  require('./configSecurityGroup.mixin.controller.js'),
])
  .controller('oraclebmcsCreateSecurityGroupCtrl', function ($scope, $uibModalInstance, $state, $controller,
                                                             cacheInitializer, infrastructureCaches,
                                                             application, securityGroup) {

    angular.extend(this, $controller('oraclebmcsConfigSecurityGroupMixin', {
      $scope: $scope,
      $uibModalInstance: $uibModalInstance,
      application: application,
      securityGroup: securityGroup
    }));
  });
