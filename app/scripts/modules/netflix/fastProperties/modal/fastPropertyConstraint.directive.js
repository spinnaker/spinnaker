'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.constraints.directive', [])
  .directive('fastPropertyConstraints', function() {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyConstraint.directive.html'),
    };
  });
