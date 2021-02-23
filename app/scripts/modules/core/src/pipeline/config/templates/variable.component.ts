import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { Variable } from './Variable';

export const VARIABLE = 'spinnaker.core.pipelineTemplate.variable.component';
module(VARIABLE, []).component(
  'variable',
  react2angular(withErrorBoundary(Variable, 'variable'), ['variableMetadata', 'variable', 'onChange']),
);
