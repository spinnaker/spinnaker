import { ApplySourceServerGroupCapacityDetails } from './ApplySourceServerGroupCapacityDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const applySourceServerGroupCapacityStage: IStageTypeConfig = {
  synthetic: true,
  key: 'applySourceServerGroupCapacity',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [ApplySourceServerGroupCapacityDetails, ExecutionDetailsTasks],
};

Registry.pipeline.registerStage(applySourceServerGroupCapacityStage);
