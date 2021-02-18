import { IStage, Registry } from '@spinnaker/core';

import { CloudfoundryRollbackClusterStageConfig } from './CloudfoundryRollbackClusterStageConfig';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  provides: 'rollbackCluster',
  key: 'rollbackCluster',
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryRollbackClusterStageConfig,
  controller: 'cfRollbackClusterStageCtrl',
  validators: [
    { type: 'requiredField', preventSave: true, fieldName: 'cluster' },
    { type: 'requiredField', preventSave: true, fieldName: 'regions' },
    { type: 'requiredField', preventSave: true, fieldName: 'credentials', fieldLabel: 'account' },
  ],
});
