'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.task.statusGlyph.directive', [])
  .directive('statusGlyph', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        item: '=',
      },
      templateUrl: require('./statusGlyph.html'),
    };

  });
