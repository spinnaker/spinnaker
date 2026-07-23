import React from 'react';
import { Registry } from '@spinnaker/core';

import { EcsServerGroupStepLabel } from '../common/EcsServerGroupStepLabel';
import { EcsCloneServerGroupStageConfig } from '../common/EcsStageConfigs';

export function registerEcsCloneServerGroupStage() {
  Registry.pipeline.registerStage({
    provides: 'cloneServerGroup',
    cloudProvider: 'ecs',
    component: EcsCloneServerGroupStageConfig,
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
}
