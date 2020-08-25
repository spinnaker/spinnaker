import { validate } from './rollbackClusterValidators';
import { Registry } from '@spinnaker/core';
import { RollbackClusterConfig } from './RollbackClusterConfig';

Registry.pipeline.registerStage({
  provides: 'rollbackCluster',
  key: 'rollbackCluster',
  cloudProvider: 'tencentcloud',
  component: RollbackClusterConfig,
  validateFn: validate,
});
