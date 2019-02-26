'use strict';

const angular = require('angular');

import { SecurityGroupState } from 'core/state';

module.exports = angular.module('spinnaker.core.securityGroup.directive', []).directive('securityGroup', [
  '$rootScope',
  function($rootScope) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./securityGroup.html'),
      scope: {
        application: '=',
        securityGroup: '=',
        sortFilter: '=',
        heading: '=',
      },
      link: function(scope) {
        scope.sortFilter = SecurityGroupState.filterModel.asFilterModel.sortFilter;
        scope.$state = $rootScope.$state;

        const securityGroup = scope.securityGroup;
        scope.srefParams = {
          application: scope.application.name,
          region: securityGroup.region,
          accountId: securityGroup.accountName,
          name: securityGroup.name,
          vpcId: securityGroup.vpcId,
          provider: securityGroup.provider,
        };
      },
    };
  },
]);
