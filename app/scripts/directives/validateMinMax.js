/*
 Adapted from Stack Overflow response:
   http://stackoverflow.com/questions/15656617/validation-not-triggered-when-data-binding-a-number-inputs-min-max-attributes#answer-15661908

 Changed to leave the model pristine on $watch updates
 */

(function() {
  function isEmpty(value) {
    return angular.isUndefined(value) || value === '' || value === null || value !== value;
  }

  angular.module('deckApp')
    .directive('validateMin', function() {
      return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope, elem, attr, ctrl) {
          scope.$watch(attr.validateMin, function(newVal, oldVal){
            if (newVal !== oldVal && (ctrl.$viewValue || ctrl.$dirty)) {
              ctrl.$setViewValue(ctrl.$viewValue);
            }
          });
          var minValidator = function(value) {
            var min = scope.$eval(attr.validateMin) || 0;
            if (!isEmpty(value) && value < min) {
              ctrl.$setValidity('validateMin', false);
              return undefined;
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
  )
  .directive('validateMax', function() {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function(scope, elem, attr, ctrl) {
        scope.$watch(attr.validateMax, function(newVal, oldVal){
          if (newVal !== oldVal && (ctrl.$viewValue || ctrl.$dirty)) {
            ctrl.$setViewValue(ctrl.$viewValue);
          }
        });
        var maxValidator = function(value) {
          var max = scope.$eval(attr.validateMax) || Infinity;
          if (!isEmpty(value) && value > max) {
            ctrl.$setValidity('validateMax', false);
            return undefined;
          } else {
            ctrl.$setValidity('validateMax', true);
            return value;
          }
        };

        ctrl.$parsers.push(maxValidator);
        ctrl.$formatters.unshift(maxValidator);
      }
    };
  });
})();
