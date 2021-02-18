import { Registry } from '@spinnaker/core';

import { ShrinkClusterConfig } from './ShrinkClusterConfig';
import { validate } from './shrinkClusterValidators';

Registry.pipeline.registerStage({
  provides: 'shrinkCluster',
  key: 'shrinkCluster',
  cloudProvider: 'tencentcloud',
  component: ShrinkClusterConfig,
  validateFn: validate,
});
