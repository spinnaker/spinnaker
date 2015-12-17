'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.modalClose.directive', [
])
  .directive('modalClose', function () {
    return {
      scope: true,
      restrict: 'E',
      templateUrl: require('./modalClose.directive.html'),
    };
});
