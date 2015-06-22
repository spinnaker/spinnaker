'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.statusGlyph.directive', [])
  .directive('statusGlyph', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        item: '=',
      },
      template: require('./statusGlyph.html'),
    };

  });
