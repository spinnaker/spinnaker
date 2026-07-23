import { UnshareServiceExecutionDetails } from './UnshareServiceExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const unshareServiceStage = {
  executionDetailsSections: [UnshareServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'unshareService',
  label: 'Unshare Service',
  description: 'Unshare a service',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(unshareServiceStage);
