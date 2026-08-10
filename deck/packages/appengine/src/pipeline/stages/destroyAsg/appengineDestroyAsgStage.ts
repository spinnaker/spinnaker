import { Registry } from '@spinnaker/core';
import { AppengineServerGroupStageConfig } from '../AppengineServerGroupStageConfig';

Registry.pipeline.registerStage({
  provides: 'destroyServerGroup',
  key: 'destroyServerGroup',
  cloudProvider: 'appengine',
  component: AppengineServerGroupStageConfig,
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
