import { Registry } from '@spinnaker/core';

import { GceEnableAsgStageConfig } from '../googleStageConfig';

export function registerGceEnableAsgStage() {
  Registry.pipeline.registerStage({
    provides: 'enableServerGroup',
    cloudProvider: 'gce',
    component: GceEnableAsgStageConfig,
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
