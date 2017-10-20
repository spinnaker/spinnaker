import { module } from 'angular';

import { PipelineConfigProvider } from 'core/pipeline';

import { APPLY_SOURCE_SERVER_GROUP_CAPACITY_DETAILS_CTRL } from './applySourceServerGroupCapacityDetails.controller';

export const APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE = 'spinnaker.core.pipeline.stage.applySourceServerGroupCapacityStage';

module(APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE, [
  APPLY_SOURCE_SERVER_GROUP_CAPACITY_DETAILS_CTRL,
  ])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'applySourceServerGroupCapacity',
      executionConfigSections: ['capacitySnapshot', 'taskStatus'],
      executionDetailsUrl: require('./applySourceServerGroupCapacityDetails.html'),
    });
  });
