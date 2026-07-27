import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const runJobStage = {
  useBaseProvider: true,
  key: 'runJob',
  label: 'Run Job',
  description: 'Runs a container',
  component: NoConfigurationStageConfig,
  restartable: true,
};

Registry.pipeline.registerStage(runJobStage);
