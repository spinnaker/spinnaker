'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.directive', [
  require('utils/lodash.js'),
])
  .directive('loadBalancer', function ($rootScope, $timeout, _) {
    return {
      restrict: 'E',
      templateUrl: require('./loadBalancer/loadBalancer.html'),
      scope: {
        loadBalancer: '=',
        displayOptions: '='
      },
      link: function(scope, el) {
        var base = el.parent().inheritedData('$uiView').state;

        scope.$state = $rootScope.$state;

        scope.loadDetails = function(event) {
          $timeout(function() {
            var loadBalancer = scope.loadBalancer;
            // anything handled by ui-sref or actual links should be ignored
            if (event.isDefaultPrevented() || (event.originalEvent && (event.originalEvent.defaultPrevented || event.originalEvent.target.href))) {
              return;
            }
            var params = {
              region: loadBalancer.region,
              accountId: loadBalancer.account,
              name: loadBalancer.name,
              vpcId: loadBalancer.vpcId,
              provider: loadBalancer.provider,
            };
            // also stolen from uiSref directive
            scope.$state.go('.loadBalancerDetails', params, {relative: base, inherit: true});
          });
        };

        scope.displayServerGroup = function (serverGroup) {
          if (scope.displayOptions.hideHealthy) {
            return _.some(serverGroup.instances, {healthState: 'Down'});
          }
          return scope.displayOptions.showServerGroups;
        };
      }
    };
  });
