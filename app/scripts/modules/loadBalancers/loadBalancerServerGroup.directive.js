'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.serverGroup', [])
  .directive('loadBalancerServerGroup', function ($rootScope) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./loadBalancer/loadBalancerServerGroup.html'),
      scope: {
        loadBalancer: '=',
        serverGroup: '=',
        displayOptions: '='
      },
      link: function (scope) {
        scope.$state = $rootScope.$state;
      }
    };
  }
).name;
