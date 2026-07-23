import React from 'react';
import { Registry } from '@spinnaker/core';

import { EcsServerGroupStepLabel } from '../common/EcsServerGroupStepLabel';

Registry.pipeline.registerStage({
  provides: 'cloneServerGroup',
  cloudProvider: 'ecs',
  templateUrl: require('./cloneServerGroupStage.html'),
  executionStepLabelComponent: (props) =>
    React.createElement(EcsServerGroupStepLabel, { action: 'Clone Server Group', useSource: true, ...props }),
  accountExtractor: (stage) => [stage.context.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
