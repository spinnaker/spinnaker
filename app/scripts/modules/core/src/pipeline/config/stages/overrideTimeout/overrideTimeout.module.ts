import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { OverrideTimeout } from './OverrideTimeout';

export const OVERRIDE_TIMEOUT_COMPONENT = 'spinnaker.core.pipeline.stage.overrideTimeout';
module(OVERRIDE_TIMEOUT_COMPONENT, []).component(
  'overrideTimeout',
  react2angular(withErrorBoundary(OverrideTimeout, 'overrideTimeout'), [
    'stageConfig',
    'stageTimeoutMs',
    'updateStageField',
  ]),
);
