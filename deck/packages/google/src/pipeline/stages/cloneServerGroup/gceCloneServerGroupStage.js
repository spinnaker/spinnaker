import { Registry } from '@spinnaker/core';

import { GceCloneServerGroupStageConfig } from '../googleStageConfig';

export const GOOGLE_PIPELINE_STAGES_CLONESERVERGROUP_GCECLONESERVERGROUPSTAGE =
  'spinnaker.gce.pipeline.stage..cloneServerGroupStage';
export const name = GOOGLE_PIPELINE_STAGES_CLONESERVERGROUP_GCECLONESERVERGROUPSTAGE;
export function registerGceCloneServerGroupStage() {
  Registry.pipeline.registerStage({
    provides: 'cloneServerGroup',
    cloudProvider: 'gce',
    component: GceCloneServerGroupStageConfig,
    validators: [
      { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'region' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
