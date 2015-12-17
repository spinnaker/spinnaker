'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.buttons.submitButton.directive', [
])
  .directive('submitButton', function () {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./submitButton.directive.html'),
      scope: {
        onClick: '&',
        isDisabled: '=',
        isNew: '=',
        submitting: '=',
        label: '=',
      }
    };
});
