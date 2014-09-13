'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
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
          var options = scope.$eval(attr.validateUnique) || [];
          if (attr.validateIgnoreCase === 'true') {
            options = options.map(function(option) { return option ? option.toLowerCase() : null; });
            value = value ? value.toLowerCase() : value;
          }
          if (options.indexOf(value) !== -1) {
            ctrl.$setValidity('validateUnique', false);
            return undefined;
          } else {
            ctrl.$setValidity('validateUnique', true);
            return value;
          }
        };

        ctrl.$parsers.push(uniqueValidator);
        ctrl.$formatters.unshift(uniqueValidator);
      }
    };
  }
);
