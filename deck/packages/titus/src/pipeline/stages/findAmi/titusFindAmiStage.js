import { Registry } from '@spinnaker/core';

import { TitusFindImageStageConfig } from '../common/TitusStageConfigs';

export const titusFindAmiStage = {
  provides: 'findImage',
  alias: 'findAmi',
  cloudProvider: 'titus',
  component: TitusFindImageStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
    { type: 'requiredField', fieldName: 'credentials' },
  ],
};

Registry.pipeline.registerStage(titusFindAmiStage);
