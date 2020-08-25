import { validate } from './scaledownClusterValidators';
import { Registry } from '@spinnaker/core';
import { ScaleDownClusterConfig } from './ScaleDownClusterConfig';

Registry.pipeline.registerStage({
  provides: 'scaleDownCluster',
  key: 'scaleDownCluster',
  cloudProvider: 'tencentcloud',
  component: ScaleDownClusterConfig,
  validateFn: validate,
});
