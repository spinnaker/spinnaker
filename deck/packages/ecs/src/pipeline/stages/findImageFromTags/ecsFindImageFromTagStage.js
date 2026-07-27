import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { EcsFindImageFromTagsExecutionDetails } from './EcsFindImageFromTagsExecutionDetails';
import { EcsFindImageFromTagsStageConfig } from '../common/EcsStageConfigs';

export function registerEcsFindImageFromTagsStage() {
  Registry.pipeline.registerStage({
    provides: 'findImageFromTags',
    cloudProvider: 'ecs',
    component: EcsFindImageFromTagsStageConfig,
    executionDetailsSections: [EcsFindImageFromTagsExecutionDetails, ExecutionDetailsTasks],
    validators: [{ type: 'requiredField', fieldName: 'imageLabelOrSha' }],
  });
}
