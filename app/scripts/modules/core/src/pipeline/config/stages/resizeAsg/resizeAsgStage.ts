import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const RESIZE_ASG_STAGE = 'spinnaker.core.pipeline.stage.resizeAsgStage';

module(RESIZE_ASG_STAGE, [PIPELINE_CONFIG_PROVIDER]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    executionConfigSections: ['resizeServerGroupConfig', 'taskStatus'],
    executionDetailsUrl: require('./resizeAsgExecutionDetails.html'),
    useBaseProvider: true,
    key: 'resizeServerGroup',
    label: 'Resize Server Group',
    description: 'Resizes a server group',
    strategy: true,
  });
});
