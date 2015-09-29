'use strict';

let angular = require('angular');

require('./securityGroupPod.directive.less');

module.exports = angular.module('spinnaker.securityGroup.pod', [])
  .directive('securityGroupPod', function() {
    return {
      restrict: 'E',
      scope: {
        grouping: '=',
        application: '=',
        parentHeading: '=',
      },
      templateUrl: require('./securityGroupPod.html'),
    };
  }).name;
