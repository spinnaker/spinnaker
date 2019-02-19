import { module } from 'angular';

import { Registry } from 'core/registry';
import { DisableAsgExecutionDetails } from './DisableAsgExecutionDetails';
import { ExecutionDetailsTasks } from '../common';

export const DISABLE_ASG_STAGE_MODULE = 'spinnaker.core.pipeline.stage.disableAsg';

module(DISABLE_ASG_STAGE_MODULE, []).config(() => {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    executionDetailsSections: [DisableAsgExecutionDetails, ExecutionDetailsTasks],
    key: 'disableServerGroup',
    label: 'Disable Server Group',
    description: 'Disables a server group',
    strategy: true,
  });
});
