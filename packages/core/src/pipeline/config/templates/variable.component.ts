import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Variable } from './Variable';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

export const VARIABLE = 'spinnaker.core.pipelineTemplate.variable.component';
module(VARIABLE, []).component(
  'variable',
  react2angular(withErrorBoundary(Variable, 'variable'), ['variableMetadata', 'variable', 'onChange']),
);
