import {module} from 'angular';
import {react2angular} from 'react2angular';
import {Variable} from './Variable';

export const VARIABLE = 'spinnaker.core.pipelineTemplate.variable.component';
module(VARIABLE, [])
  .component('variable', react2angular(Variable, ['variableMetadata', 'variable', 'onChange']));
