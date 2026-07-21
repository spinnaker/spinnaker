import { module } from 'angular';

import { TemplatePlanErrors } from './TemplatePlanErrors';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const TEMPLATE_PLAN_ERRORS = 'spinnaker.core.templatePlanErrors.component';
module(TEMPLATE_PLAN_ERRORS, []).component(
  'templatePlanErrors',
  angularComponentFromReact(TemplatePlanErrors, 'templatePlanErrors', ['errors']),
);
