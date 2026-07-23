import React from 'react';
import { Registry } from '@spinnaker/core';

import { EcsServerGroupStepLabel } from '../common/EcsServerGroupStepLabel';

Registry.pipeline.registerStage({
  provides: 'disableServerGroup',
  alias: 'disableAsg',
  cloudProvider: 'ecs',
  templateUrl: require('./disableAsgStage.html'),
  executionStepLabelComponent: (props) =>
    React.createElement(EcsServerGroupStepLabel, { action: 'Disable Server Group', ...props }),
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
