'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.tag.directive', [])
  .directive('loadBalancersTag', function () {
    return {
      restrict: 'E',
      replace: false,
      templateUrl: require('./loadBalancer/loadBalancersTag.html'),
      scope: {
        application: '=',
        serverGroup: '=',
        maxDisplay: '='
      },
      link: function(scope) {
        scope.popover = { show: false };
        scope.application.loadBalancers.ready().then(() => {
          scope.loadBalancers = scope.serverGroup.loadBalancers.map(lbName => {
            let serverGroup = scope.serverGroup;
            let [match] = scope.application.loadBalancers.data
              .filter(lb => lb.name === lbName && lb.account === serverGroup.account && lb.region === serverGroup.region);
            return match;
          });
        });
      }
    };
  }
);
