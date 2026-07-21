import { module } from 'angular';

import { Variable } from './Variable';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const VARIABLE = 'spinnaker.core.pipelineTemplate.variable.component';
module(VARIABLE, []).component(
  'variable',
  angularComponentFromReact(Variable, 'variable', ['variableMetadata', 'variable', 'onChange']),
);
