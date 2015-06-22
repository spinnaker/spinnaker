'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.tag', [])
  .directive('loadBalancersTag', function () {
    return {
      restrict: 'E',
      replace: false,
      template: require('./loadBalancer/loadBalancersTag.html'),
      scope: {
        serverGroup: '=',
        maxDisplay: '='
      },
      link: function(scope) {
        scope.popover = { show: false };
      }
    };
  }
);
