import { IStage, Registry } from '@spinnaker/core';

import { CloudfoundryResizeAsgStageConfig } from './CloudfoundryResizeAsgStageConfig';
import { IInstanceFieldSizeValidationConfig } from '../../config/validation/instanceSize.validator';

const diskValidator: IInstanceFieldSizeValidationConfig = {
  type: 'cfInstanceSizeField',
  fieldName: 'diskQuota',
  fieldLabel: 'Disk Mb',
  min: 256,
  preventSave: true,
};

const instanceCountValidator: IInstanceFieldSizeValidationConfig = {
  type: 'cfInstanceSizeField',
  fieldName: 'capacity.desired',
  fieldLabel: 'Instances',
  min: 0,
  preventSave: true,
};

const memoryValidator: IInstanceFieldSizeValidationConfig = {
  type: 'cfInstanceSizeField',
  fieldName: 'memory',
  fieldLabel: 'Mem Mb',
  min: 256,
  preventSave: true,
};

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryResizeAsgStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  key: 'resizeServerGroup',
  provides: 'resizeServerGroup',
  validators: [
    {
      type: 'cfTargetImpedance',
      message:
        'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    instanceCountValidator,
    memoryValidator,
    diskValidator,
  ],
});
