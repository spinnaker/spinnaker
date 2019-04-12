import { module } from 'angular';

import { EXECUTION_WINDOW_ACTIONS_COMPONENT } from './executionWindowActions.component';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

import './executionWindows.less';

export const EXECUTION_WINDOWS_STAGE_MODULE = 'spinnaker.core.pipeline.stage.executionWindows';

module(EXECUTION_WINDOWS_STAGE_MODULE, [
  require('./executionWindows.directive').name,
  EXECUTION_WINDOW_ACTIONS_COMPONENT,
  require('../stage.module').name,
  STAGE_COMMON_MODULE,
]);
