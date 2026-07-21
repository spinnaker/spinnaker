import { module } from 'angular';

import { ExecutionWindows } from './ExecutionWindows';
import { angularComponentFromReact } from '../../../../angular/angularComponentFromReact';

export const EXECUTION_WINDOWS = 'spinnaker.core.pipeline.stage.executionWindows.directive';
module(EXECUTION_WINDOWS, []).component(
  'executionWindows',
  angularComponentFromReact(ExecutionWindows, 'executionWindows', [
    'restrictExecutionDuringTimeWindow',
    'restrictedExecutionWindow',
    'skipWindowText',
    'updateStageField',
  ]),
);
