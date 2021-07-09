import { module } from 'angular';
import { react2angular } from 'react2angular';

import { StageFailureMessage } from './StageFailureMessage';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const STAGE_FAILURE_MESSAGE_COMPONENT = 'spinnaker.core.pipeline.stageFailureMessage.component';
module(STAGE_FAILURE_MESSAGE_COMPONENT, []).component(
  'stageFailureMessage',
  react2angular(withErrorBoundary(StageFailureMessage, 'stageFailureMessage'), ['message', 'messages', 'stage']),
);
