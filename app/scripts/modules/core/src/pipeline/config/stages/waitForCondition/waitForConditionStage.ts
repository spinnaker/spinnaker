import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { WaitForConditionTransformer } from './waitForCondition.transformer';
import { WaitForConditionExecutionDetails } from './WaitForConditionExecutionDetails';

Registry.pipeline.registerStage({
  label: 'Wait For Condition',
  description: 'Waits until a set of conditions are met',
  key: 'waitForCondition',
  executionDetailsSections: [WaitForConditionExecutionDetails, ExecutionDetailsTasks],
  synthetic: true,
});

Registry.pipeline.registerTransformer(new WaitForConditionTransformer());
