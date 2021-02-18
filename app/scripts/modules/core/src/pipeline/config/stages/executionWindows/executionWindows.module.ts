import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { ExecutionWindows } from './ExecutionWindows';

export const EXECUTION_WINDOWS = 'spinnaker.core.pipeline.stage.executionWindows.directive';
module(EXECUTION_WINDOWS, []).component(
  'executionWindows',
  react2angular(withErrorBoundary(ExecutionWindows, 'executionWindows'), [
    'restrictExecutionDuringTimeWindow',
    'restrictedExecutionWindow',
    'skipWindowText',
    'updateStageField',
  ]),
);
