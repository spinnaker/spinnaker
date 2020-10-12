'use strict';

import { module } from 'angular';

export const CORE_VALIDATION_VALIDATEUNIQUE_DIRECTIVE = 'spinnaker.core.validation.unique.directive';
export const name = CORE_VALIDATION_VALIDATEUNIQUE_DIRECTIVE; // for backwards compatibility
module(CORE_VALIDATION_VALIDATEUNIQUE_DIRECTIVE, []).directive('validateUnique', function () {
  return {
    restrict: 'A',
    require: 'ngModel',
    link: function (scope, elem, attr, ctrl) {
      scope.$watch(
        attr.validateUnique,
        function (newVal, oldVal) {
          if (newVal !== oldVal && (ctrl.$viewValue || ctrl.$dirty)) {
            ctrl.$validate();
          }
        },
        true,
      );
      const uniqueValidator = function (value) {
        let options = scope.$eval(attr.validateUnique) || [];
        let test = value;
        if (attr.validateIgnoreCase === 'true') {
          options = options.map(function (option) {
            return option ? option.toLowerCase() : null;
          });
          test = value ? value.toLowerCase() : value;
        }
        return !options.includes(test);
      };

      ctrl.$validators.validateUnique = uniqueValidator;
    },
  };
});
