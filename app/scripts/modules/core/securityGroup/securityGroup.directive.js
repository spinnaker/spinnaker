'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.securityGroup.directive', [])
  .directive('securityGroup', function ($rootScope, $timeout, SecurityGroupFilterModel) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./securityGroup.html'),
      scope: {
        application: '=',
        securityGroup: '=',
        sortFilter: '='
      },
      link: function (scope, el) {
        var base = el.parent().inheritedData('$uiView').state;
        var securityGroup = scope.securityGroup;

        scope.sortFilter = SecurityGroupFilterModel.sortFilter;
        scope.$state = $rootScope.$state;

        scope.waypoint = [securityGroup.account, securityGroup.region, securityGroup.name].join(':');

        scope.loadDetails = function(e) {
          $timeout(function() {
            var securityGroup = scope.securityGroup;
            // anything handled by ui-sref or actual links should be ignored
            if (e.isDefaultPrevented() || (e.originalEvent && e.originalEvent.target.href)) {
              return;
            }
            var params = {
              application: scope.application.name,
              region: securityGroup.region,
              accountId: securityGroup.accountName,
              name: securityGroup.name,
              vpcId: securityGroup.vpcId,
              provider: securityGroup.provider,
            };

            if (angular.equals(scope.$state.params, params)) {
              // already there
              return;
            }
            // also stolen from uiSref directive
            scope.$state.go('.securityGroupDetails', params, {relative: base, inherit: true});
          });
        };

      }
    };
  }
);
