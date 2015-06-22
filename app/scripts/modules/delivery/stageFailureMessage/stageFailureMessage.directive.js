'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.executionDetails.stageFailureMessage.directive', [])
  .directive('stageFailureMessage', function () {
    return {
      restrict: 'E',
      template: require('./stageFailureMessage.html'),
      scope: {
        isFailed: '=',
        message: '=',
      },
    };
  });
