'use strict';

import { MonitorPipelineStageExecutionDetails } from './MonitorPipelineStageExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const monitorPipelineStage = {
  label: 'Monitor Pipeline',
  description: 'Monitors pipeline execution',
  key: 'monitorPipeline',
  component: NoConfigurationStageConfig,
  restartable: true,
  synthetic: true,
  executionDetailsSections: [MonitorPipelineStageExecutionDetails, ExecutionDetailsTasks],
};

Registry.pipeline.registerStage(monitorPipelineStage);
