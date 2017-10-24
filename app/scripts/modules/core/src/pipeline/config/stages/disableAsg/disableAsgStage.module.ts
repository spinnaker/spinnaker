import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const DISABLE_ASG_STAGE_MODULE = 'spinnaker.core.pipeline.stage.disableAsg';

module(DISABLE_ASG_STAGE_MODULE, [
  PIPELINE_CONFIG_PROVIDER,
])
.config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    executionConfigSections: ['disableServerGroupConfig', 'taskStatus'],
    executionDetailsUrl: require('./disableAsgExecutionDetails.template.html'),
    key: 'disableServerGroup',
    label: 'Disable Server Group',
    description: 'Disables a server group',
    strategy: true,
  });
});
