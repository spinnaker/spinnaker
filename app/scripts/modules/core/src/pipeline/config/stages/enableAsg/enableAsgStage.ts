import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { EnableAsgExecutionDetails } from './EnableAsgExecutionDetails';
import { ExecutionDetailsTasks } from '../core';

export const ENABLE_ASG_STAGE = 'spinnaker.core.pipeline.stage.enableAsg';

module(ENABLE_ASG_STAGE, [PIPELINE_CONFIG_PROVIDER]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    executionDetailsSections: [EnableAsgExecutionDetails, ExecutionDetailsTasks],
    key: 'enableServerGroup',
    label: 'Enable Server Group',
    description: 'Enables a server group',
    strategy: true,
  });
});
