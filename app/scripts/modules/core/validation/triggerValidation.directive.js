'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.validation.trigger.directive', [])
  .directive('triggerValidation', function () {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attr, ctrl) {
        var watches = attr.triggerValidation.split(',');
        watches.forEach(function(watchValue) {
          scope.$watch(watchValue, function () {
            if (ctrl.$viewValue) {
              ctrl.$validate();
            }
          });
        });
      }
    };
  }
);
