import { module } from 'angular';
import { react2angular } from 'react2angular';

import { OverrideTimeout } from './OverrideTimeout';
import { withErrorBoundary } from '../../../../presentation/SpinErrorBoundary';

export const OVERRIDE_TIMEOUT_COMPONENT = 'spinnaker.core.pipeline.stage.overrideTimeout';
module(OVERRIDE_TIMEOUT_COMPONENT, []).component(
  'overrideTimeout',
  react2angular(withErrorBoundary(OverrideTimeout, 'overrideTimeout'), [
    'stageConfig',
    'stageTimeoutMs',
    'updateStageField',
  ]),
);
