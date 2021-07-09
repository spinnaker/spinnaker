import { Registry } from '@spinnaker/core';

import { RollbackClusterConfig } from './RollbackClusterConfig';
import { validate } from './rollbackClusterValidators';

Registry.pipeline.registerStage({
  provides: 'rollbackCluster',
  key: 'rollbackCluster',
  cloudProvider: 'tencentcloud',
  component: RollbackClusterConfig,
  validateFn: validate,
});
