'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.autoScroll', [])
    .directive('autoScroll', function ($timeout) {
      return {
        restrict: 'A',
        link: function(scope, elem, attrs) {
          scope.$watch(attrs.autoScroll, function() {
            $timeout(function() {
              elem.parent().scrollTop(elem.height());
            });
          }, true);
        }
      };
  });
