import { Registry } from '@spinnaker/core';
import { AppengineServerGroupStageConfig } from '../AppengineServerGroupStageConfig';

Registry.pipeline.registerStage({
  provides: 'enableServerGroup',
  key: 'enableServerGroup',
  cloudProvider: 'appengine',
  component: AppengineServerGroupStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
