'use strict';

import { module } from 'angular';

import { MonitorPipelineStageExecutionDetails } from './MonitorPipelineStageExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_MONITORPIPELINE_MONITORPIPELINESTAGE =
  'spinnaker.core.pipeline.stage.monitorPipelineStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_MONITORPIPELINE_MONITORPIPELINESTAGE; // for backwards compatibility
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
module(CORE_PIPELINE_CONFIG_STAGES_MONITORPIPELINE_MONITORPIPELINESTAGE, []);
