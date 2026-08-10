import { Registry } from '@spinnaker/core';

import { GceFindImageStageConfig } from '../googleStageConfig';

export function registerGceFindAmiStage() {
  Registry.pipeline.registerStage({
    provides: 'findImage',
    cloudProvider: 'gce',
    component: GceFindImageStageConfig,
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials' },
    ],
  });
}
