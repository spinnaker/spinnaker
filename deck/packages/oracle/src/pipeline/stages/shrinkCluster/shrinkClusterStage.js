'use strict';

import { Registry } from '@spinnaker/core';

import { OracleStageConfig } from '../OracleStageConfig';

export const oracleShrinkClusterStage = {
  provides: 'shrinkCluster',
  cloudProvider: 'oracle',
  component: OracleStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerOracleShrinkClusterStage() {
  Registry.pipeline.registerStage(oracleShrinkClusterStage);
}

registerOracleShrinkClusterStage();
