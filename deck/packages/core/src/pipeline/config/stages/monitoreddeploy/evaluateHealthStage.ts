import { DeploymentMonitorExecutionDetails } from './DeploymentMonitorExecutionDetails';
import { NoConfigurationStageConfig } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const evaluateHealthStage: IStageTypeConfig = {
  synthetic: true,
  key: 'evaluateDeploymentHealth',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [DeploymentMonitorExecutionDetails],
};

Registry.pipeline.registerStage(evaluateHealthStage);
