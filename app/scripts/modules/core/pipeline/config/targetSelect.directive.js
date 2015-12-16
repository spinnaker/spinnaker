'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.pipeline.targetSelect.directive', [
  ])
  .directive('targetSelect', function() {
    return {
      restrict: 'E',
      scope: {
        options: '=',
        model: '='
      },
      templateUrl: require('./targetSelect.html'),
    };
  });
