import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Execution } from './Execution';

export const EXECUTION_COMPONENT = 'spinnaker.core.delivery.group.executionGroup.execution.component';
module(EXECUTION_COMPONENT, [])
  .component('execution', react2angular(Execution, ['application', 'execution', 'standalone']));
