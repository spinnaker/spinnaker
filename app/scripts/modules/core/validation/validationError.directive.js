'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.validation.error.directive', [])
  .directive('validationError', function () {
    return {
      restrict: 'E',
      templateUrl: require('./validationError.html'),
      scope: {
        message: '@'
      }
    };
  }
);
