import { WaitExecutionDetails } from './WaitExecutionDetails';
import { WaitExecutionLabel } from './WaitExecutionLabel';
import { WaitStageConfig } from './WaitStageConfig';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

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
