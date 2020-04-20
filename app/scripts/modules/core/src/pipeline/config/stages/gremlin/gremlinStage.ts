import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { GremlinExecutionDetails } from './GremlinExecutionDetails';
import { GremlinStageConfig } from './GremlinStageConfig';

Registry.pipeline.registerStage({
  label: 'Gremlin',
  description: 'Runs a chaos experiment using Gremlin',
  key: 'gremlin',
  component: GremlinStageConfig,
  executionDetailsSections: [GremlinExecutionDetails, ExecutionDetailsTasks],
  strategy: true,
  validators: [
    { type: 'requiredField', fieldName: 'gremlinCommandTemplateId' },
    { type: 'requiredField', fieldName: 'gremlinTargetTemplateId' },
    { type: 'requiredField', fieldName: 'gremlinApiKey' },
  ],
});
