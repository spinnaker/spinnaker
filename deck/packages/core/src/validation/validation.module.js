import { module } from 'angular';

import { CORE_VALIDATION_TRIGGERVALIDATION_DIRECTIVE } from './triggerValidation.directive';
import { CORE_VALIDATION_VALIDATEUNIQUE_DIRECTIVE } from './validateUnique.directive';
import { CORE_VALIDATION_VALIDATIONERROR_DIRECTIVE } from './validationError.directive';

('use strict');

export const CORE_VALIDATION_VALIDATION_MODULE = 'spinnaker.core.validation';
export const name = CORE_VALIDATION_VALIDATION_MODULE; // for backwards compatibility
module(CORE_VALIDATION_VALIDATION_MODULE, [
  CORE_VALIDATION_VALIDATEUNIQUE_DIRECTIVE,
  CORE_VALIDATION_TRIGGERVALIDATION_DIRECTIVE,
  CORE_VALIDATION_VALIDATIONERROR_DIRECTIVE,
]);
