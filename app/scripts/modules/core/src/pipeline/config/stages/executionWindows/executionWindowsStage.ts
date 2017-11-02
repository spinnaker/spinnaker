import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline';
import { ExecutionWindowExecutionDetails } from './ExecutionWindowExecutionDetails';
import { ExecutionDetailsTasks } from '../core';

export const EXECUTION_WINDOWS_STAGE = 'spinnaker.core.pipeline.stage.executionWindowsStage';

module(EXECUTION_WINDOWS_STAGE, [PIPELINE_CONFIG_PROVIDER])
  .config(function(pipelineConfigProvider: PipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Restrict Execution During',
      synthetic: true,
      description: 'Restricts execution of stage during specified period of time',
      key: 'restrictExecutionDuringTimeWindow',
      executionDetailsSections: [ExecutionWindowExecutionDetails, ExecutionDetailsTasks],
    });
  })
  .run((pipelineConfig: PipelineConfigProvider, executionWindowsTransformer: any) => {
    pipelineConfig.registerTransformer(executionWindowsTransformer);
  });
