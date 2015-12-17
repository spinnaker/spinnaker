'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.tag.directive', [])
  .directive('loadBalancersTag', function () {
    return {
      restrict: 'E',
      replace: false,
      templateUrl: require('./loadBalancer/loadBalancersTag.html'),
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
