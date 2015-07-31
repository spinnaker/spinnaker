'use strict';

let angular = require('angular');

require('./statusGlyph.html');

module.exports = angular.module('spinnaker.statusGlyph.directive', [])
  .directive('statusGlyph', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        item: '=',
      },
      templateUrl: require('./statusGlyph.html'),
    };

  }).name;
