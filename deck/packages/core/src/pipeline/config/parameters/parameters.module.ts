import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Parameters } from './Parameters';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

export const PARAMETERS = 'spinnaker.core.pipeline.parameters.parameters';
module(PARAMETERS, []).component(
  'parameters',
  react2angular(withErrorBoundary(Parameters, 'parameters'), [
    'addParameter',
    'parameters',
    'pipelineName',
    'removeParameter',
    'updateParameter',
    'updateAllParameters',
  ]),
);
