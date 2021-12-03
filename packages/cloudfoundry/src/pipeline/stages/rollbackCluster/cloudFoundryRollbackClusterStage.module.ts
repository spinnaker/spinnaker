import type { IStage } from '@spinnaker/core';
import { Registry } from '@spinnaker/core';

import { CloudFoundryRollbackClusterStageConfig } from './CloudFoundryRollbackClusterStageConfig';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'rollbackCluster',
  key: 'rollbackCluster',
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryRollbackClusterStageConfig,
  controller: 'cfRollbackClusterStageCtrl',
  validators: [
    { type: 'requiredField', preventSave: true, fieldName: 'cluster' },
    { type: 'requiredField', preventSave: true, fieldName: 'regions' },
    { type: 'requiredField', preventSave: true, fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
