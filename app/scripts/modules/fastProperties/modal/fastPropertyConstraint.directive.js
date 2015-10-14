'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperty.constraints.directive', [])
  .directive('fastPropertyConstraints', function() {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyConstraint.directive.html'),
    };
  }).name;
