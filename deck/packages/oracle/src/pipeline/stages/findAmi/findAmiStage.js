'use strict';

import { Registry } from '@spinnaker/core';

import { OracleStageConfig } from '../OracleStageConfig';

export const oracleFindAmiStage = {
  provides: 'findImage',
  cloudProvider: 'oracle',
  component: OracleStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials' },
  ],
};

export function registerOracleFindAmiStage() {
  Registry.pipeline.registerStage(oracleFindAmiStage);
}

registerOracleFindAmiStage();
