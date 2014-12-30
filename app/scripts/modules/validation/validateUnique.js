'use strict';


angular.module('deckApp.validation')
  .directive('validateUnique', function () {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attr, ctrl) {
        scope.$watch(attr.validateUnique, function (newVal, oldVal) {
          if (newVal !== oldVal && (ctrl.$viewValue || ctrl.$dirty)) {
            ctrl.$setViewValue(ctrl.$viewValue);
          }
        });
        var uniqueValidator = function (value) {
          var options = scope.$eval(attr.validateUnique) || [],
              test = value;
          if (attr.validateIgnoreCase === 'true') {
            options = options.map(function(option) { return option ? option.toLowerCase() : null; });
            test = value ? value.toLowerCase() : value;
          }
          ctrl.$setValidity('validateUnique', options.indexOf(test) === -1);
          return value;
        };

        ctrl.$parsers.push(uniqueValidator);
        ctrl.$formatters.unshift(uniqueValidator);
      }
    };
  }
);
