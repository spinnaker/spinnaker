import { Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  provides: 'enableServerGroup',
  alias: 'enableAsg',
  cloudProvider: 'ecs',
  templateUrl: require('./enableAsgStage.html'),
  executionStepLabelUrl: require('./enableAsgStepLabel.html'),
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
