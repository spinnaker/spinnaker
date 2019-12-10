'use strict';

const angular = require('angular');

export const CORE_VALIDATION_VALIDATION_MODULE = 'spinnaker.core.validation';
export const name = CORE_VALIDATION_VALIDATION_MODULE; // for backwards compatibility
angular.module(CORE_VALIDATION_VALIDATION_MODULE, [
  require('./validateUnique.directive').name,
  require('./triggerValidation.directive').name,
  require('./validationError.directive').name,
]);
