'use strict';

let angular = require('angular');

require('./stageFailureMessage.html');

module.exports = angular.module('spinnaker.delivery.executionDetails.stageFailureMessage.directive', [])
  .directive('stageFailureMessage', function () {
    return {
      restrict: 'E',
      templateUrl: require('./stageFailureMessage.html'),
      scope: {
        isFailed: '=',
        message: '=',
      },
    };
  }).name;
