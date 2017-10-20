import { module } from 'angular';

import { DESTROY_ASG_EXECUTION_DETAILS_CTRL } from './templates/destroyAsgExecutionDetails.controller';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const DESTROY_ASG_STAGE = 'spinnaker.core.pipeline.stage.destroyAsg';

module(DESTROY_ASG_STAGE, [
  DESTROY_ASG_EXECUTION_DETAILS_CTRL,
  PIPELINE_CONFIG_PROVIDER,
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'destroyServerGroup',
      label: 'Destroy Server Group',
      description: 'Destroys a server group',
      strategy: true,
    });
  });
