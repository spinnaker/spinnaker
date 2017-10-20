import { module } from 'angular';

import { ENABLE_ASG_EXECUTION_DETAILS_CTRL } from './templates/enableAsgStage.controller';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const ENABLE_ASG_STAGE_MODULE = 'spinnaker.core.pipeline.stage.enableAsg';

module(ENABLE_ASG_STAGE_MODULE, [
  ENABLE_ASG_EXECUTION_DETAILS_CTRL,
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    key: 'enableServerGroup',
    label: 'Enable Server Group',
    description: 'Enables a server group',
    strategy: true,
  });
});
