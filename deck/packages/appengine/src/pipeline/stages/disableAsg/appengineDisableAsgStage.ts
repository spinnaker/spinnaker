import { Registry } from '@spinnaker/core';
import { AppengineServerGroupStageConfig } from '../AppengineServerGroupStageConfig';

Registry.pipeline.registerStage({
  provides: 'disableServerGroup',
  key: 'disableServerGroup',
  cloudProvider: 'appengine',
  component: AppengineServerGroupStageConfig,
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
