'use strict';

import { BakeExecutionLabel, ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { OracleStageConfig } from '../OracleStageConfig';

export const oracleBakeStage = {
  provides: 'bake',
  cloudProvider: 'oracle',
  label: 'Bake',
  description: 'Bakes an image',
  component: OracleStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  executionLabelComponent: BakeExecutionLabel,
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'accountName' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'baseOs' },
    { type: 'requiredField', fieldName: 'upgrade' },
    { type: 'requiredField', fieldName: 'cloudProviderType' },
    { type: 'requiredField', fieldName: 'amiName', fieldLabel: 'Image Name' },
  ],
  restartable: true,
};

export function registerOracleBakeStage() {
  Registry.pipeline.registerStage(oracleBakeStage);
}

registerOracleBakeStage();
