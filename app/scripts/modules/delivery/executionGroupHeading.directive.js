'use strict';
let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.executionGroupHeading.directive', [
  require('./triggers/triggersTag.directive.js'),
])
  .directive('executionGroupHeading', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        value: '=',
        scale: '=',
        filter: '=',
        executions: '=',
        configurations: '=',
        application: '=',
      },
      template: require('./executionGroupHeading.html'),
      controller: 'executionGroupHeading as ctrl',
    };
  });
