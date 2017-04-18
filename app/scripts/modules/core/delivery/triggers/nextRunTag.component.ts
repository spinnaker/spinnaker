import { module } from 'angular';
import { react2angular } from 'react2angular';

import { NextRunTag } from './NextRunTag';

export const NEXT_RUN_TAG_COMPONENT = 'spinnaker.core.delivery.triggers.nextRun';
module(NEXT_RUN_TAG_COMPONENT, [])
  .component('nextRunTag', react2angular(NextRunTag, ['pipeline']));
