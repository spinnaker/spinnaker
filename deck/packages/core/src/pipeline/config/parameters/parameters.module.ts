import { module } from 'angular';

import { Parameters } from './Parameters';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const PARAMETERS = 'spinnaker.core.pipeline.parameters.parameters';
module(PARAMETERS, []).component(
  'parameters',
  angularComponentFromReact(Parameters, 'parameters', [
    'addParameter',
    'parameters',
    'pipelineName',
    'removeParameter',
    'updateParameter',
    'updateAllParameters',
  ]),
);
