import { validate } from './disableClusterValidators';
import { Registry } from '@spinnaker/core';
import { DisableClusterConfig } from './DisableClusterConfig';

Registry.pipeline.registerStage({
  provides: 'disableCluster',
  key: 'disableCluster',
  cloudProvider: 'tencentcloud',
  component: DisableClusterConfig,
  validateFn: validate,
});
