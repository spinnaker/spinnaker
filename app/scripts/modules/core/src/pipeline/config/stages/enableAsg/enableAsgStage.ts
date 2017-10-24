import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const ENABLE_ASG_STAGE = 'spinnaker.core.pipeline.stage.enableAsg';

module(ENABLE_ASG_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    executionConfigSections: ['enableServerGroupConfig', 'taskStatus'],
    executionDetailsUrl: require('./enableAsgExecutionDetails.template.html'),
    key: 'enableServerGroup',
    label: 'Enable Server Group',
    description: 'Enables a server group',
    strategy: true,
  });
});
