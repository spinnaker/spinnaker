'use strict';

const angular = require('angular');
import { get, last } from 'lodash';

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
      link: function (scope, el) {
        var base = last(get(el.parent().inheritedData('$uiView'), '$cfg.path')).state.name;

        scope.sortFilter = SecurityGroupFilterModel.sortFilter;
        scope.$state = $rootScope.$state;

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
