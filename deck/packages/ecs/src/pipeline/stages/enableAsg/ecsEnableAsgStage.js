import React from 'react';
import { Registry } from '@spinnaker/core';

import { EcsServerGroupStepLabel } from '../common/EcsServerGroupStepLabel';
import { EcsEnableAsgStageConfig } from '../common/EcsStageConfigs';

export function registerEcsEnableServerGroupStage() {
  Registry.pipeline.registerStage({
    provides: 'enableServerGroup',
    alias: 'enableAsg',
    cloudProvider: 'ecs',
    component: EcsEnableAsgStageConfig,
    executionStepLabelComponent: (props) =>
      React.createElement(EcsServerGroupStepLabel, { action: 'Enable Server Group', ...props }),
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
