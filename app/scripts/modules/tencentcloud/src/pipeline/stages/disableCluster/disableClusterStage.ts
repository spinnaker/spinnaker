import { Registry } from '@spinnaker/core';

import { DisableClusterConfig } from './DisableClusterConfig';
import { validate } from './disableClusterValidators';

Registry.pipeline.registerStage({
  provides: 'disableCluster',
  key: 'disableCluster',
  cloudProvider: 'tencentcloud',
  component: DisableClusterConfig,
  validateFn: validate,
});
