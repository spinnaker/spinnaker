'use strict';

import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { OracleStageConfig } from '../OracleStageConfig';

export const oracleFindImageFromTagsStage = {
  provides: 'findImageFromTags',
  cloudProvider: 'oracle',
  component: OracleStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  validators: [
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'packageName' },
  ],
};

export function registerOracleFindImageFromTagsStage() {
  Registry.pipeline.registerStage(oracleFindImageFromTagsStage);
}

registerOracleFindImageFromTagsStage();
