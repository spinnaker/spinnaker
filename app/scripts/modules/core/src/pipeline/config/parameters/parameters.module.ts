import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Parameters } from './Parameters';

export const PARAMETERS = 'spinnaker.core.pipeline.parameters.parameters';
module(PARAMETERS, []).component(
  'parameters',
  react2angular(Parameters, [
    'addParameter',
    'parameters',
    'pipelineName',
    'removeParameter',
    'updateParameter',
    'updateAllParameters',
  ]),
);
