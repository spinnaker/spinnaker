'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.validation')
  .directive('triggerValidation', function () {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attr, ctrl) {
        var watches = attr.triggerValidation.split(',');
        watches.forEach(function(watchValue) {
          scope.$watch(watchValue, function () {
            if (ctrl.$viewValue) {
              ctrl.$setViewValue(ctrl.$viewValue);
            }
          });
        });
      }
    };
  }
).name;
