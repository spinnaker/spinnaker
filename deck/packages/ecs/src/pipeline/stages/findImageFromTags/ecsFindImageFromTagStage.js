import { Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  provides: 'findImageFromTags',
  cloudProvider: 'ecs',
  templateUrl: require('./findImageFromTagsStage.html'),
  executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
  executionConfigSections: ['findImageConfig', 'taskStatus'],
  validators: [{ type: 'requiredField', fieldName: 'imageLabelOrSha' }],
});
