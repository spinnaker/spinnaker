import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../core';
import { EvaluateVariablesExecutionDetails } from './EvaluateVariablesExecutionDetails';
import { EvaluateVariablesStageConfig } from './EvaluateVariablesStageConfig';

Registry.pipeline.registerStage({
  label: 'Evaluate Variables',
  description:
    'Evaluates variables for use in SpEL expressions in downstream stages. Variables can be accessed by their key.',
  key: 'evaluateVariables',
  component: EvaluateVariablesStageConfig,
  executionDetailsSections: [EvaluateVariablesExecutionDetails, ExecutionDetailsTasks],
  useCustomTooltip: true,
  strategy: true,
  validators: [{ type: 'requiredField', fieldName: 'variables' }],
});
