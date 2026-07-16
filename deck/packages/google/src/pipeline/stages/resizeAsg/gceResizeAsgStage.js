import { Registry } from '@spinnaker/core';

import { GceResizeServerGroupStageConfig } from '../googleStageConfig';

export const GOOGLE_PIPELINE_STAGES_RESIZEASG_GCERESIZEASGSTAGE = 'spinnaker.gce.pipeline.stage..resizeAsgStage';
export const name = GOOGLE_PIPELINE_STAGES_RESIZEASG_GCERESIZEASGSTAGE;
export function registerGceResizeAsgStage() {
  Registry.pipeline.registerStage({
    provides: 'resizeServerGroup',
    cloudProvider: 'gce',
    component: GceResizeServerGroupStageConfig,
    accountExtractor: (stage) => [stage.context.credentials],
    configAccountExtractor: (stage) => [stage.credentials],
    supportsCustomTimeout: true,
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
      },
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'action' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
