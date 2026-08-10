import { Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

export const awsFindAmiStage = {
  provides: 'findImage',
  alias: 'findAmi',
  cloudProvider: 'aws',
  component: AmazonStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials' },
  ],
};

export function registerAwsFindAmiStage() {
  Registry.pipeline.registerStage(awsFindAmiStage);
}

registerAwsFindAmiStage();
