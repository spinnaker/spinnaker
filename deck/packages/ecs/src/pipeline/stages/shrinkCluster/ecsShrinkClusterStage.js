import { Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  provides: 'shrinkCluster',
  cloudProvider: 'ecs',
  templateUrl: require('./shrinkClusterStage.html'),
  accountExtractor: (stage) => [stage.context.credentials],
  configAccountExtractor: (stage) => [stage.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
