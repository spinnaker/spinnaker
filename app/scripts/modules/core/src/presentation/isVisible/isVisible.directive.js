'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.presentation.isVisible.directive', [
])
  .directive('isVisible', function () {
    return function (scope, element, attr) {
      scope.$watch(attr.isVisible, function (visible) {
        element.css('visibility', visible ? 'visible' : 'hidden');
      });
    };
});
