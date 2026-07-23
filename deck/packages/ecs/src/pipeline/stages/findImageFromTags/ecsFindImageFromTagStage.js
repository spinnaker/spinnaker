import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { EcsFindImageFromTagsExecutionDetails } from './EcsFindImageFromTagsExecutionDetails';

Registry.pipeline.registerStage({
  provides: 'findImageFromTags',
  cloudProvider: 'ecs',
  templateUrl: require('./findImageFromTagsStage.html'),
  executionDetailsSections: [EcsFindImageFromTagsExecutionDetails, ExecutionDetailsTasks],
  validators: [{ type: 'requiredField', fieldName: 'imageLabelOrSha' }],
});
