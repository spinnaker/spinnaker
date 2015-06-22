'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperty.constraints.directive', [])
  .directive('fastPropertyConstraints', function() {
    return {
      restrict: 'E',
      //scope: {},
      template: require('./fastPropertyConstraint.directive.html'),
      //link: function (scope) {
      //
      //}
    };
  });
