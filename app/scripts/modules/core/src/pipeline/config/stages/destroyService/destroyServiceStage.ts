import { module } from 'angular';

import { Registry } from 'core/registry';
import { ExecutionDetailsTasks } from '../common';
import { DestroyServiceExecutionDetails } from './DestroyServiceExecutionDetails';

export const DESTROY_SERVICE_STAGE = 'spinnaker.core.pipeline.stage.destroyService';

module(DESTROY_SERVICE_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [DestroyServiceExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'destroyService',
    label: 'Destroy Service',
    description: 'Destroys a service',
    strategy: true,
  });
});
