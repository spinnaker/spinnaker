import { module } from 'angular';

import { Registry } from 'core/registry';
import { ExecutionDetailsTasks } from '../core';
import { DeleteServiceExecutionDetails } from './DeleteServiceExecutionDetails';

export const DELETE_SERVICE_STAGE = 'spinnaker.core.pipeline.stage.deleteService';

module(DELETE_SERVICE_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [DeleteServiceExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'deleteService',
    label: 'Delete Service',
    description: 'Deletes a service',
    strategy: true,
  });
});
