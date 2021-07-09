import { Registry } from '@spinnaker/core';

import { ScaleDownClusterConfig } from './ScaleDownClusterConfig';
import { validate } from './scaledownClusterValidators';

Registry.pipeline.registerStage({
  provides: 'scaleDownCluster',
  key: 'scaleDownCluster',
  cloudProvider: 'tencentcloud',
  component: ScaleDownClusterConfig,
  validateFn: validate,
});
