import { module } from 'angular';

import { Registry } from 'core/registry';

import { ApplySourceServerGroupCapacityDetails } from './ApplySourceServerGroupCapacityDetails';
import { ExecutionDetailsTasks } from '../common';

export const APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE =
  'spinnaker.core.pipeline.stage.applySourceServerGroupCapacityStage';

module(APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'applySourceServerGroupCapacity',
    executionDetailsSections: [ApplySourceServerGroupCapacityDetails, ExecutionDetailsTasks],
  });
});
