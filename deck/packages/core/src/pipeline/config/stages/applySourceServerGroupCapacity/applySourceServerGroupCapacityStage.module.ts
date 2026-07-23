import { module } from 'angular';

import { ApplySourceServerGroupCapacityDetails } from './ApplySourceServerGroupCapacityDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE =
  'spinnaker.core.pipeline.stage.applySourceServerGroupCapacityStage';

export const applySourceServerGroupCapacityStage: IStageTypeConfig = {
  synthetic: true,
  key: 'applySourceServerGroupCapacity',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [ApplySourceServerGroupCapacityDetails, ExecutionDetailsTasks],
};

Registry.pipeline.registerStage(applySourceServerGroupCapacityStage);
module(APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE, []);
