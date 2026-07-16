import { Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  provides: 'cloneServerGroup',
  cloudProvider: 'ecs',
  templateUrl: require('./cloneServerGroupStage.html'),
  executionStepLabelUrl: require('./cloneServerGroupStepLabel.html'),
  accountExtractor: (stage) => [stage.context.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
