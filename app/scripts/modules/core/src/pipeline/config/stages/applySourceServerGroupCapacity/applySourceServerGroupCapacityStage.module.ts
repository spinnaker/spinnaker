import { module } from 'angular';

import { ApplySourceServerGroupCapacityDetails } from './ApplySourceServerGroupCapacityDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

export const APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE =
  'spinnaker.core.pipeline.stage.applySourceServerGroupCapacityStage';

module(APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'applySourceServerGroupCapacity',
    executionDetailsSections: [ApplySourceServerGroupCapacityDetails, ExecutionDetailsTasks],
  });
});
