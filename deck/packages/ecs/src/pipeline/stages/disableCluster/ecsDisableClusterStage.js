import { Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  provides: 'disableCluster',
  cloudProvider: 'ecs',
  templateUrl: require('./disableClusterStage.html'),
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
