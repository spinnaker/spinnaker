import { module } from 'angular';

import { PipelineConfigProvider } from 'core/pipeline';

import { ApplySourceServerGroupCapacityDetails } from './ApplySourceServerGroupCapacityDetails';
import { ExecutionDetailsTasks } from '../core';

export const APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE =
  'spinnaker.core.pipeline.stage.applySourceServerGroupCapacityStage';

module(APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE, []).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerStage({
    synthetic: true,
    key: 'applySourceServerGroupCapacity',
    executionDetailsSections: [ApplySourceServerGroupCapacityDetails, ExecutionDetailsTasks],
  });
});
