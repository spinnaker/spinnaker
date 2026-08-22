import { Registry } from '@spinnaker/core';
import { AppengineShrinkClusterStageConfig } from '../AppengineServerGroupStageConfig';

Registry.pipeline.registerStage({
  provides: 'shrinkCluster',
  key: 'shrinkCluster',
  cloudProvider: 'appengine',
  component: AppengineShrinkClusterStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
