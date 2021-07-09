import { EvaluateVariablesExecutionDetails } from './EvaluateVariablesExecutionDetails';
import { EvaluateVariablesStageConfig, validateEvaluateVariablesStage } from './EvaluateVariablesStageConfig';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  label: 'Evaluate Variables',
  description: 'Evaluates variables for use in downstream stages.',
  key: 'evaluateVariables',
  defaults: {
    failOnFailedExpressions: true,
  },
  component: EvaluateVariablesStageConfig,
  executionDetailsSections: [EvaluateVariablesExecutionDetails, ExecutionDetailsTasks],
  strategy: true,
  validateFn: validateEvaluateVariablesStage,
});
