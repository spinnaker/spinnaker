import { WaitForConditionExecutionDetails } from './WaitForConditionExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';
import { WaitForConditionTransformer } from './waitForCondition.transformer';

Registry.pipeline.registerStage({
  label: 'Wait For Condition',
  description: 'Waits until a set of conditions are met',
  key: 'waitForCondition',
  executionDetailsSections: [WaitForConditionExecutionDetails, ExecutionDetailsTasks],
  synthetic: true,
});

Registry.pipeline.registerTransformer(new WaitForConditionTransformer());
