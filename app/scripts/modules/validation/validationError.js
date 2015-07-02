'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.validation.error', [])
  .directive('validationError', function () {
    return {
      restrict: 'E',
      templateUrl: require('./validationError.html'),
      scope: {
        message: '@'
      }
    };
  }
).name;
