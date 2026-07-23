import { WaitForConditionExecutionDetails } from './WaitForConditionExecutionDetails';
import { ExecutionDetailsTasks, NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';
import { WaitForConditionTransformer } from './waitForCondition.transformer';

export const waitForConditionStage = {
  label: 'Wait For Condition',
  description: 'Waits until a set of conditions are met',
  key: 'waitForCondition',
  component: NoConfigurationStageConfig,
  executionDetailsSections: [WaitForConditionExecutionDetails, ExecutionDetailsTasks],
  synthetic: true,
};

Registry.pipeline.registerStage(waitForConditionStage);

Registry.pipeline.registerTransformer(new WaitForConditionTransformer());
