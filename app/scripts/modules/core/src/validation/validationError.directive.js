'use strict';

import { module } from 'angular';

export const CORE_VALIDATION_VALIDATIONERROR_DIRECTIVE = 'spinnaker.core.validation.error.directive';
export const name = CORE_VALIDATION_VALIDATIONERROR_DIRECTIVE; // for backwards compatibility
module(CORE_VALIDATION_VALIDATIONERROR_DIRECTIVE, []).directive('validationError', function () {
  return {
    restrict: 'E',
    templateUrl: require('./validationError.html'),
    scope: {
      message: '@',
    },
  };
});
