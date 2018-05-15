import { module } from 'angular';
import { react2angular } from 'react2angular';

import { StageManualComponents } from './StageManualComponents';

export const STAGE_MANUAL_COMPONENTS = 'spinnaker.core.pipeline.manualExecution.stageManualComponents.component';
module(STAGE_MANUAL_COMPONENTS, []).component(
  'stageManualComponents',
  react2angular(StageManualComponents, ['command', 'components']),
);
