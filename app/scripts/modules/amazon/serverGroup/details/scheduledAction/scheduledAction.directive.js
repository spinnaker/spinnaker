'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.aws.instance.details.scheduledAction.directive', [
])
  .directive('scheduledAction', function() {
    return {
      restrict: 'E',
      scope: {
        action: '='
      },
      templateUrl: require('./scheduledAction.directive.html'),
    };
  });
