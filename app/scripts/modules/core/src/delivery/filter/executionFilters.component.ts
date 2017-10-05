import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ExecutionFilters } from './ExecutionFilters';

export const EXECUTION_FILTERS_COMPONENT = 'spinnaker.core.delivery.filter.executionFilters.component';
module(EXECUTION_FILTERS_COMPONENT, [])
  .component('executionFilters', react2angular(ExecutionFilters, ['application']));
