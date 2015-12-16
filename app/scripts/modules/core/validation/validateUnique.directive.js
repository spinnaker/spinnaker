'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.validation.unique.directive', [])
  .directive('validateUnique', function () {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attr, ctrl) {
        scope.$watch(attr.validateUnique, function (newVal, oldVal) {
          if (newVal !== oldVal && (ctrl.$viewValue || ctrl.$dirty)) {
            ctrl.$validate();
          }
        }, true);
        var uniqueValidator = function (value) {
          var options = scope.$eval(attr.validateUnique) || [],
              test = value;
          if (attr.validateIgnoreCase === 'true') {
            options = options.map(function(option) { return option ? option.toLowerCase() : null; });
            test = value ? value.toLowerCase() : value;
          }
          return options.indexOf(test) === -1;
        };

        ctrl.$validators.validateUnique = uniqueValidator;
      }
    };
  }
);
