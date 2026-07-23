import React from 'react';
import { Registry } from '@spinnaker/core';

import { EcsServerGroupStepLabel } from '../common/EcsServerGroupStepLabel';
import { EcsResizeAsgStageConfig } from '../common/EcsStageConfigs';

export function registerEcsResizeServerGroupStage() {
  Registry.pipeline.registerStage({
    provides: 'resizeServerGroup',
    alias: 'resizeAsg',
    cloudProvider: 'ecs',
    component: EcsResizeAsgStageConfig,
    executionConfigSections: ['resizeServerGroupConfig', 'taskStatus'],
    executionStepLabelComponent: (props) =>
      React.createElement(EcsServerGroupStepLabel, { action: 'Resize Server Group', ...props }),
    accountExtractor: (stage) => [stage.context.credentials],
    configAccountExtractor: (stage) => [stage.credentials],
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
      },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'action' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
