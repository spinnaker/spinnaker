'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.securityGroup.directive', [])
  .directive('securityGroup', function ($rootScope, $timeout, SecurityGroupFilterModel) {
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
      link: function (scope) {
        scope.sortFilter = SecurityGroupFilterModel.sortFilter;
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
      }
    };
  }
);
