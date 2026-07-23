import React from 'react';
import { Registry } from '@spinnaker/core';

import { EcsServerGroupStepLabel } from '../common/EcsServerGroupStepLabel';

Registry.pipeline.registerStage({
  provides: 'enableServerGroup',
  alias: 'enableAsg',
  cloudProvider: 'ecs',
  templateUrl: require('./enableAsgStage.html'),
  executionStepLabelComponent: (props) =>
    React.createElement(EcsServerGroupStepLabel, { action: 'Enable Server Group', ...props }),
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
