import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { ApplyEntityTagsExecutionDetails } from './ApplyEntityTagsExecutionDetails';
import { ApplyEntityTagsStageConfig } from './ApplyEntityTagsStageConfig';

Registry.pipeline.registerStage({
  label: 'Entity Tags',
  description: 'Applies entity tags to a resource.',
  key: 'upsertEntityTags',
  defaults: {
    tags: [],
  },
  component: ApplyEntityTagsStageConfig,
  executionDetailsSections: [ApplyEntityTagsExecutionDetails, ExecutionDetailsTasks],
  useCustomTooltip: true,
  strategy: true,
  validators: [{ type: 'requiredField', fieldName: 'entityRef.entityType' }],
});
