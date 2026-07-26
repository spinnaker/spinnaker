'use strict';

import { PipelineParametersExecutionDetails } from './PipelineParametersExecutionDetails';
import { PipelineStageConfig } from './PipelineStageConfig';
import { PipelineStageExecutionDetails } from './PipelineStageExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE = 'spinnaker.core.pipeline.stage.pipelineStage';
export const pipelineStage = {
  label: 'Pipeline',
  description: 'Runs a pipeline',
  key: 'pipeline',
  restartable: true,
  component: PipelineStageConfig,
  producesArtifacts: true,
  executionDetailsSections: [PipelineStageExecutionDetails, PipelineParametersExecutionDetails, ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  validators: [{ type: 'requiredField', fieldName: 'pipeline' }],
};

Registry.pipeline.registerStage(pipelineStage);
