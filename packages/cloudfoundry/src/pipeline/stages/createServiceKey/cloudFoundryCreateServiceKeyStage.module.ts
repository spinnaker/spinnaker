import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { CloudFoundryCreateServiceKeyStageConfig } from './CloudFoundryCreateServiceKeyStageConfig';
import { CloudFoundryServiceKeyExecutionDetails } from '../../../presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryCreateServiceKeyStageConfig,
  description: 'Create a service key',
  executionDetailsSections: [CloudFoundryServiceKeyExecutionDetails, ExecutionDetailsTasks],
  key: 'createServiceKey',
  label: 'Create Service Key',
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceInstanceName', preventSave: true },
    { type: 'requiredField', fieldName: 'serviceKeyName', preventSave: true },
  ],
});
