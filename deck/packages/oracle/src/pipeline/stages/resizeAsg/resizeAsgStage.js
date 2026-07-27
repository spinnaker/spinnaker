'use strict';

import { Registry } from '@spinnaker/core';

import { OracleStageConfig } from '../OracleStageConfig';

export const oracleResizeAsgStage = {
  provides: 'resizeServerGroup',
  cloudProvider: 'oracle',
  component: OracleStageConfig,
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
};

export function registerOracleResizeAsgStage() {
  Registry.pipeline.registerStage(oracleResizeAsgStage);
}

registerOracleResizeAsgStage();
