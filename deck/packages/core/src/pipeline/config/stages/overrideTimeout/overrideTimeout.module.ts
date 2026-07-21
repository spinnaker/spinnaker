import { module } from 'angular';

import { OverrideTimeout } from './OverrideTimeout';
import { angularComponentFromReact } from '../../../../angular/angularComponentFromReact';

export const OVERRIDE_TIMEOUT_COMPONENT = 'spinnaker.core.pipeline.stage.overrideTimeout';
module(OVERRIDE_TIMEOUT_COMPONENT, []).component(
  'overrideTimeout',
  angularComponentFromReact(OverrideTimeout, 'overrideTimeout', ['stageConfig', 'stageTimeoutMs', 'updateStageField']),
);
