import { validate } from './shrinkClusterValidators';
import { Registry } from '@spinnaker/core';
import { ShrinkClusterConfig } from './ShrinkClusterConfig';

Registry.pipeline.registerStage({
  provides: 'shrinkCluster',
  key: 'shrinkCluster',
  cloudProvider: 'tencentcloud',
  component: ShrinkClusterConfig,
  validateFn: validate,
});
