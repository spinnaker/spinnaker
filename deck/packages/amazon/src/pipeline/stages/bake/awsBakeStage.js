import { BakeExecutionLabel, ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsBakeStage = {
  provides: 'bake',
  cloudProvider: 'aws',
  label: 'Bake',
  description: 'Bakes an image',
  component: AmazonStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  executionLabelComponent: BakeExecutionLabel,
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'package' },
    { type: 'requiredField', fieldName: 'regions' },
    {
      type: 'upstreamVersionProvided',
      checkParentTriggers: true,
      getMessage: (labels) =>
        'Bake stages should always have a stage or trigger preceding them that provides version information: ' +
        '<ul>' +
        labels.map((label) => `<li>${label}</li>`).join('') +
        '</ul>' +
        'Otherwise, Spinnaker will bake and deploy the most-recently built package.',
    },
  ],
  restartable: true,
};

export function registerAwsBakeStage() {
  Registry.pipeline.registerStage(awsBakeStage);
}

registerAwsBakeStage();
