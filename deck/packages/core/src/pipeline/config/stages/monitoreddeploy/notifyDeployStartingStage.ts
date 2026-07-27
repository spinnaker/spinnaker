import { DeploymentMonitorExecutionDetails } from './DeploymentMonitorExecutionDetails';
import { NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const notifyDeployStartingStage: IStageTypeConfig = {
  synthetic: true,
  key: 'notifyDeployStarting',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [DeploymentMonitorExecutionDetails],
};

Registry.pipeline.registerStage(notifyDeployStartingStage);
