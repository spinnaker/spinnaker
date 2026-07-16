import { Registry } from '@spinnaker/core';

import { GceFindImageStageConfig } from '../googleStageConfig';

export const GOOGLE_PIPELINE_STAGES_FINDAMI_GCEFINDAMISTAGE = 'spinnaker.gce.pipeline.stage..findAmiStage';
export const name = GOOGLE_PIPELINE_STAGES_FINDAMI_GCEFINDAMISTAGE;
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
