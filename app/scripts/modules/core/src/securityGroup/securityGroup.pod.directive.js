'use strict';

const angular = require('angular');

import './securityGroupPod.directive.less';

module.exports = angular.module('spinnaker.core.securityGroup.pod.directive', [])
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
  });
