import { Registry } from '@spinnaker/core';

import { GceTargetServerGroupStageConfig } from '../googleStageConfig';

export const GOOGLE_PIPELINE_STAGES_ENABLEASG_GCEENABLEASGSTAGE = 'spinnaker.gce.pipeline.stage..enableAsgStage';
export const name = GOOGLE_PIPELINE_STAGES_ENABLEASG_GCEENABLEASGSTAGE;
export function registerGceEnableAsgStage() {
  Registry.pipeline.registerStage({
    provides: 'enableServerGroup',
    cloudProvider: 'gce',
    component: GceTargetServerGroupStageConfig,
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
