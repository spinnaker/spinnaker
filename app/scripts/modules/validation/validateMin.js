'use strict';


/*
 Adapted from Stack Overflow response:
 http://stackoverflow.com/questions/15656617/validation-not-triggered-when-data-binding-a-number-inputs-min-max-attributes#answer-15661908

 Changed to leave the model pristine on $watch updates
 */

angular.module('spinnaker.validation')
  .directive('validateMin', function (isEmpty) {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attr, ctrl) {
        scope.$watch(attr.validateMin, function (newVal, oldVal) {
          if (newVal !== oldVal && (ctrl.$viewValue || ctrl.$dirty)) {
            ctrl.$setViewValue(ctrl.$viewValue);
          }
        });
        var minValidator = function (value) {
          var min = scope.$eval(attr.validateMin) || 0;
          if (!isEmpty(value) && value < min) {
            ctrl.$setValidity('validateMin', false);
            return value;
          } else {
            ctrl.$setValidity('validateMin', true);
            return value;
          }
        };

        ctrl.$parsers.push(minValidator);
        ctrl.$formatters.push(minValidator);
      }
    };
  }
);
