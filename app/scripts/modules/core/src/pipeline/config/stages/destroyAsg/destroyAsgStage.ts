import { module } from 'angular';

import { Registry } from 'core/registry';
import { ExecutionDetailsTasks } from '../common';
import { DestroyAsgExecutionDetails } from './DestroyAsgExecutionDetails';

export const DESTROY_ASG_STAGE = 'spinnaker.core.pipeline.stage.destroyAsg';

module(DESTROY_ASG_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [DestroyAsgExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'destroyServerGroup',
    label: 'Destroy Server Group',
    description: 'Destroys a server group',
    strategy: true,
  });
});
