import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ExecutionWindows } from './ExecutionWindows';

export const EXECUTION_WINDOWS = 'spinnaker.core.pipeline.stage.executionWindows.directive';
module(EXECUTION_WINDOWS, []).component(
  'executionWindows',
  react2angular(ExecutionWindows, [
    'restrictExecutionDuringTimeWindow',
    'restrictedExecutionWindow',
    'skipWindowText',
    'updateStageField',
  ]),
);
