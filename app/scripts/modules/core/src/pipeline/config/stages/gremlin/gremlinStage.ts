import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';

import { ExecutionDetailsTasks } from '../core';
import { GremlinExecutionDetails } from './GremlinExecutionDetails';
import { GremlinStageConfig } from './GremlinStageConfig';

if (SETTINGS.feature.gremlinEnabled) {
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
}
