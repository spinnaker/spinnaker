import { module } from 'angular';

import { EXECUTION_WINDOW_ACTIONS_COMPONENT } from './executionWindowActions.component';
import { EXECUTION_WINDOWS_STAGE } from './executionWindowsStage';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';

import './executionWindows.less';

export const EXECUTION_WINDOWS_STAGE_MODULE = 'spinnaker.core.pipeline.stage.executionWindows';

module(EXECUTION_WINDOWS_STAGE_MODULE, [
  EXECUTION_WINDOWS_STAGE,
  require('./executionWindows.transformer').name,
  require('./executionWindows.directive').name,
  EXECUTION_WINDOW_ACTIONS_COMPONENT,
  require('../stage.module').name,
  STAGE_CORE_MODULE,
]);
