import {module} from 'angular';
import {react2angular} from 'react2angular';

import {SkipWait} from './SkipWait';

export const SKIP_WAIT_COMPONENT = 'spinnaker.core.pipeline.config.stages.wait.component';
module(SKIP_WAIT_COMPONENT, [])
  .component('skipWaitSelector', react2angular(SkipWait, ['execution', 'stage', 'application']));
