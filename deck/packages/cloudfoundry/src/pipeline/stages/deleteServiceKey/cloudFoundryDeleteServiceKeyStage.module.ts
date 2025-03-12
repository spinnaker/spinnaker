import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { CloudFoundryDeleteServiceKeyStageConfig } from './CloudFoundryDeleteServiceKeyStageConfig';
import { CloudFoundryServiceKeyExecutionDetails } from '../../../presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryDeleteServiceKeyStageConfig,
  description: 'Delete a service key',
  executionDetailsSections: [CloudFoundryServiceKeyExecutionDetails, ExecutionDetailsTasks],
  key: 'deleteServiceKey',
  label: 'Delete Service Key',
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceKeyName', preventSave: true },
  ],
});
