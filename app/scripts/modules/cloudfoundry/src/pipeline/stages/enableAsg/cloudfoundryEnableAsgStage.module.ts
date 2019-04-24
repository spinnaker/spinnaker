import { CloudfoundryAsgStageConfig } from 'cloudfoundry/presentation';
import { IStage, Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryAsgStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  key: 'enableServerGroup',
  provides: 'enableServerGroup',
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
