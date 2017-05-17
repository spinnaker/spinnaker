import {module} from 'angular';
import {react2angular} from 'react2angular';

import {ExecutionWindowActions} from './ExecutionWindowActions';

export const EXECUTION_WINDOW_ACTIONS_COMPONENT = 'spinnaker.core.pipeline.config.stages.executionWindowActions.component';
module(EXECUTION_WINDOW_ACTIONS_COMPONENT, [])
  .component('executionWindowActions', react2angular(ExecutionWindowActions, ['execution', 'stage', 'application']));
