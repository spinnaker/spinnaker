import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TemplatePlanErrors } from './TemplatePlanErrors';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

export const TEMPLATE_PLAN_ERRORS = 'spinnaker.core.templatePlanErrors.component';
module(TEMPLATE_PLAN_ERRORS, []).component(
  'templatePlanErrors',
  react2angular(withErrorBoundary(TemplatePlanErrors, 'templatePlanErrors'), ['errors']),
);
