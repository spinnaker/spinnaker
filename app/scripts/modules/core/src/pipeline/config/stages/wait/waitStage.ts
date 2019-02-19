import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { WaitExecutionDetails } from './WaitExecutionDetails';
import { WaitExecutionLabel } from './WaitExecutionLabel';
import { WaitStageConfig } from './WaitStageConfig';

export const DEFAULT_SKIP_WAIT_TEXT = 'The pipeline will proceed immediately, marking this stage completed.';

Registry.pipeline.registerStage({
  label: 'Wait',
  description: 'Waits a specified period of time',
  key: 'wait',
  component: WaitStageConfig,
  executionDetailsSections: [WaitExecutionDetails, ExecutionDetailsTasks],
  executionLabelComponent: WaitExecutionLabel,
  useCustomTooltip: true,
  strategy: true,
  validators: [{ type: 'requiredField', fieldName: 'waitTime' }],
});
