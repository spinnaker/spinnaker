import { module } from 'angular';
import { react2angular } from 'react2angular';

import { EXECUTION_FILTER_MODEL } from 'core/delivery/filter/executionFilter.model';
import { ExecutionGroups } from './ExecutionGroups';

export const EXECUTION_GROUPS_COMPONENT = 'spinnaker.core.delivery.main.executionGroups.component';
module(EXECUTION_GROUPS_COMPONENT, [
  EXECUTION_FILTER_MODEL,
])
  .component('executionGroups', react2angular(ExecutionGroups, ['application']));
