'use strict';

import { Registry } from '@spinnaker/core';

import { OracleStageConfig } from '../OracleStageConfig';

export const oracleScaleDownClusterStage = {
  provides: 'scaleDownCluster',
  cloudProvider: 'oracle',
  component: OracleStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    {
      type: 'requiredField',
      fieldName: 'remainingFullSizeServerGroups',
      fieldLabel: 'Keep [X] full size Server Groups',
    },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
  strategy: true,
};

export function registerOracleScaleDownClusterStage() {
  Registry.pipeline.registerStage(oracleScaleDownClusterStage);
}

registerOracleScaleDownClusterStage();
