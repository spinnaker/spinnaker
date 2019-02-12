'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.canary.status.directive', []).directive('canaryStatus', function() {
  return {
    restrict: 'E',
    scope: {
      status: '=',
    },
    template: '<span class="label label-default label-{{statusLabel}}">{{status}}</span>',
    link: function(scope) {
      function applyLabel() {
        scope.statusLabel =
          scope.status === 'LAUNCHED'
            ? 'launched'
            : scope.status === 'RUNNING'
            ? 'running'
            : scope.status === 'SUCCEEDED'
            ? 'succeeded'
            : scope.status === 'FAILED'
            ? 'failed'
            : scope.status === 'TERMINATED'
            ? 'terminated'
            : scope.status === 'CANCELED'
            ? 'canceled'
            : 'unknown';
      }
      scope.$watch('status', applyLabel);
    },
  };
});
