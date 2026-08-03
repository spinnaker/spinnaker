import { DestroyServiceExecutionDetails } from './DestroyServiceExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const destroyServiceStage = {
  executionDetailsSections: [DestroyServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'destroyService',
  label: 'Destroy Service',
  description: 'Destroys a service',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(destroyServiceStage);
