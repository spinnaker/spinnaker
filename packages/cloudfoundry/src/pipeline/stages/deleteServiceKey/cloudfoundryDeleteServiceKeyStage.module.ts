import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import { CloudfoundryDeleteServiceKeyStageConfig } from './CloudfoundryDeleteServiceKeyStageConfig';
import { CloudfoundryServiceKeyExecutionDetails } from '../../../presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryDeleteServiceKeyStageConfig,
  description: 'Delete a service key',
  executionDetailsSections: [CloudfoundryServiceKeyExecutionDetails, ExecutionDetailsTasks],
  key: 'deleteServiceKey',
  label: 'Delete Service Key',
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceKeyName', preventSave: true },
  ],
});
