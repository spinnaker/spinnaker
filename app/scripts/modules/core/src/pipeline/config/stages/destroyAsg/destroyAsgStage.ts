import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const DESTROY_ASG_STAGE = 'spinnaker.core.pipeline.stage.destroyAsg';

module(DESTROY_ASG_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      executionConfigSections: ['destroyServerGroupConfig', 'taskStatus'],
      executionDetailsUrl: require('./destroyAsgExecutionDetails.template.html'),
      useBaseProvider: true,
      key: 'destroyServerGroup',
      label: 'Destroy Server Group',
      description: 'Destroys a server group',
      strategy: true,
    });
  });
