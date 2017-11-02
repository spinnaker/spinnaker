import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { ExecutionDetailsTasks } from '../core';
import { DestroyAsgExecutionDetails } from './DestroyAsgExecutionDetails';

export const DESTROY_ASG_STAGE = 'spinnaker.core.pipeline.stage.destroyAsg';

module(DESTROY_ASG_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      executionDetailsSections: [DestroyAsgExecutionDetails, ExecutionDetailsTasks],
      useBaseProvider: true,
      key: 'destroyServerGroup',
      label: 'Destroy Server Group',
      description: 'Destroys a server group',
      strategy: true,
    });
  });
