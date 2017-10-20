import { module } from 'angular';

import { DISABLE_ASG_EXECUTION_DETAILS_CTRL } from './templates/disableAsgStage.controller';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const DISABLE_ASG_STAGE_MODULE = 'spinnaker.core.pipeline.stage.disableAsg';

module(DISABLE_ASG_STAGE_MODULE, [
  DISABLE_ASG_EXECUTION_DETAILS_CTRL,
  PIPELINE_CONFIG_PROVIDER,
])
.config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    key: 'disableServerGroup',
    label: 'Disable Server Group',
    description: 'Disables a server group',
    strategy: true,
  });
});
