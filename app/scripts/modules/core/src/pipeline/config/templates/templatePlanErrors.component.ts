import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { TemplatePlanErrors } from './TemplatePlanErrors';

export const TEMPLATE_PLAN_ERRORS = 'spinnaker.core.templatePlanErrors.component';
module(TEMPLATE_PLAN_ERRORS, []).component(
  'templatePlanErrors',
  react2angular(withErrorBoundary(TemplatePlanErrors, 'templatePlanErrors'), ['errors']),
);
