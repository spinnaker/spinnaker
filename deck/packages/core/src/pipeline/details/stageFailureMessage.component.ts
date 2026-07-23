import { module } from 'angular';

import { StageFailureMessage } from './StageFailureMessage';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const STAGE_FAILURE_MESSAGE_COMPONENT = 'spinnaker.core.pipeline.stageFailureMessage.component';
module(STAGE_FAILURE_MESSAGE_COMPONENT, []).component(
  'stageFailureMessage',
  angularComponentFromReact(StageFailureMessage, 'stageFailureMessage', ['message', 'messages', 'stage']),
);
