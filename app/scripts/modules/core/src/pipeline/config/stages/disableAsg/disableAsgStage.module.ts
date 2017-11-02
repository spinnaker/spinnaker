import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { DisableAsgExecutionDetails } from './DisableAsgExecutionDetails';
import { ExecutionDetailsTasks } from '../core';

export const DISABLE_ASG_STAGE_MODULE = 'spinnaker.core.pipeline.stage.disableAsg';

module(DISABLE_ASG_STAGE_MODULE, [
  PIPELINE_CONFIG_PROVIDER,
])
.config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    executionDetailsSections: [DisableAsgExecutionDetails, ExecutionDetailsTasks],
    key: 'disableServerGroup',
    label: 'Disable Server Group',
    description: 'Disables a server group',
    strategy: true,
  });
});
