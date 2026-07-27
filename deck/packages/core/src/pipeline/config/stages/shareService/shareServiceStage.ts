import { ShareServiceExecutionDetails } from './ShareServiceExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const shareServiceStage = {
  executionDetailsSections: [ShareServiceExecutionDetails, ExecutionDetailsTasks],
  useBaseProvider: true,
  key: 'shareService',
  label: 'Share Service',
  description: 'Share a service',
  component: NoConfigurationStageConfig,
  strategy: true,
};

Registry.pipeline.registerStage(shareServiceStage);
