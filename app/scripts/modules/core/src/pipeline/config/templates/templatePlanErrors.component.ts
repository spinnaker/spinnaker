import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TemplatePlanErrors } from 'core/pipeline/config/templates/TemplatePlanErrors';

export const TEMPLATE_PLAN_ERRORS = 'spinnaker.templatePlanErrors.component';
module(TEMPLATE_PLAN_ERRORS, [])
  .component('templatePlanErrors', react2angular(TemplatePlanErrors, ['errors']));
