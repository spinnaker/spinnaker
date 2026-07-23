import React from 'react';
import { Registry } from '@spinnaker/core';

import { EcsServerGroupStepLabel } from '../common/EcsServerGroupStepLabel';

export function registerEcsDestroyServerGroupStage() {
  Registry.pipeline.registerStage({
    provides: 'destroyServerGroup',
    alias: 'destroyAsg',
    cloudProvider: 'ecs',
    templateUrl: require('./destroyAsgStage.html'),
    executionStepLabelComponent: (props) =>
      React.createElement(EcsServerGroupStepLabel, { action: 'Destroy Server Group', ...props }),
    accountExtractor: (stage) => [stage.context.credentials],
    configAccountExtractor: (stage) => [stage.credentials],
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
      },
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}
