import { IStage, Registry } from '@spinnaker/core';
import { CloudfoundryAsgStageConfig } from '../../../presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryAsgStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  key: 'disableServerGroup',
  provides: 'disableServerGroup',
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
