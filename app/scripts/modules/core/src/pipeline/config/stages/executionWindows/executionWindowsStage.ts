import { module } from 'angular';

import { Registry } from 'core/registry';

import { ExecutionWindowExecutionDetails } from './ExecutionWindowExecutionDetails';
import { ExecutionDetailsTasks } from '../common';

export const EXECUTION_WINDOWS_STAGE = 'spinnaker.core.pipeline.stage.executionWindowsStage';

module(EXECUTION_WINDOWS_STAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      label: 'Restrict Execution During',
      synthetic: true,
      description: 'Restricts execution of stage during specified period of time',
      key: 'restrictExecutionDuringTimeWindow',
      executionDetailsSections: [ExecutionWindowExecutionDetails, ExecutionDetailsTasks],
    });
  })
  .run((executionWindowsTransformer: any) => {
    Registry.pipeline.registerTransformer(executionWindowsTransformer);
  });
