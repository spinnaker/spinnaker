'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.loadBalancerSelector', [])
  .directive('gceServerGroupLoadBalancersSelector', function(gceServerGroupConfigurationService, infrastructureCaches) {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupLoadBalancersDirective.html'),
      link: function(scope) {

        scope.getLoadBalancerRefreshTime = function() {
          return infrastructureCaches.loadBalancers.getStats().ageMax;
        };

        scope.refreshLoadBalancers = function() {
          scope.refreshing = true;
          gceServerGroupConfigurationService.refreshLoadBalancers(scope.command).then(function() {
            scope.refreshing = false;
          });
        };
      }
    };
  });
