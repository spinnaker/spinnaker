'use strict';

let angular = require('angular');

require('./loadBalancerPod.directive.less');

module.exports = angular.module('spinnaker.core.loadBalancer.pod', [])
  .directive('loadBalancerPod', function() {
    return {
      restrict: 'E',
      scope: {
        grouping: '=',
        application: '=',
        parentHeading: '=',
      },
      templateUrl: require('./loadBalancerPod.html'),
    };
  });
