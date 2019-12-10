'use strict';

const angular = require('angular');

export const CORE_VALIDATION_TRIGGERVALIDATION_DIRECTIVE = 'spinnaker.core.validation.trigger.directive';
export const name = CORE_VALIDATION_TRIGGERVALIDATION_DIRECTIVE; // for backwards compatibility
angular.module(CORE_VALIDATION_TRIGGERVALIDATION_DIRECTIVE, []).directive('triggerValidation', function() {
  return {
    restrict: 'A',
    require: 'ngModel',
    link: function(scope, elem, attr, ctrl) {
      var watches = attr.triggerValidation.split(',');
      watches.forEach(function(watchValue) {
        scope.$watch(watchValue, function() {
          if (ctrl.$viewValue) {
            ctrl.$validate();
          }
        });
      });
    },
  };
});
