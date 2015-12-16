'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionDetails.stageFailureMessage.directive', [])
  .directive('stageFailureMessage', function () {
    return {
      restrict: 'E',
      templateUrl: require('./stageFailureMessage.html'),
      scope: {
        isFailed: '=',
        message: '=',
        messages: '=',
      },
    };
  });
