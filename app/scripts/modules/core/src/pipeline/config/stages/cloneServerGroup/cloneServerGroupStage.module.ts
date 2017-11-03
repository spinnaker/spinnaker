import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';
import { CloneServerGroupExecutionDetails } from './CloneServerGroupExecutionDetails';
import { ExecutionDetailsTasks } from '../core';

export const CLONE_SERVER_GROUP_STAGE = 'spinnaker.core.pipeline.stage.cloneServerGroup';
module(CLONE_SERVER_GROUP_STAGE, [
  require('../stage.module.js').name,
  PIPELINE_CONFIG_PROVIDER,
  STAGE_CORE_MODULE,
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    useBaseProvider: true,
    key: 'cloneServerGroup',
    label: 'Clone Server Group',
    executionDetailsSections: [CloneServerGroupExecutionDetails, ExecutionDetailsTasks],
    description: 'Clones a server group',
    strategy: false,
  });
});
