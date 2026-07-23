import { DeployServiceExecutionDetails } from './DeployServiceExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const deployServiceStage = {
  executionDetailsSections: [DeployServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'deployService',
  label: 'Deploy Service',
  description: 'Deploy a service',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(deployServiceStage);
