import { RunMultiplePipelinesStageConfig } from './RunMultiplePipelinesStageConfig';
import { RunMultiplePipelinesStageExecutionDetails } from './RunMultiplePipelinesStageExecutionDetails';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';

Registry.pipeline.registerStage({
  label: 'Run Multiple Pipelines',
  description: 'Stage that triggers pipelines based on yaml file',
  key: 'runMultiplePipelines',
  component: RunMultiplePipelinesStageConfig,
  executionDetailsSections: [RunMultiplePipelinesStageExecutionDetails, ExecutionDetailsTasks],
});
