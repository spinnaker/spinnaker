import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ExecutionBuildNumber } from './ExecutionBuildNumber';

export const EXECUTION_BUILD_NUMBER_COMPONENT = 'spinnaker.core.delivery.execution.build.number.component';
module(EXECUTION_BUILD_NUMBER_COMPONENT, [])
  .component('executionBuildNumber', react2angular(ExecutionBuildNumber, ['execution']));
