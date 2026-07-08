import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/tencentcloud.help';
import { TencentcloudImageReader } from './image';
import logo from './logo/tencentcloud.logo.svg';
// load pipeline stage
import './pipeline/stages/disableCluster/disableClusterStage';
import './pipeline/stages/rollbackCluster/rollbackClusterStage';
import './pipeline/stages/scaleDownCluster/scaleDownClusterStage';
import './pipeline/stages/shrinkCluster/shrinkClusterStage';
import './validation/ApplicationNameValidator';

CloudProviderRegistry.registerProvider('tencentcloud', {
  name: 'tencentcloud',
  logo: {
    path: logo,
  },
  image: {
    reader: TencentcloudImageReader,
  },
});

DeploymentStrategyRegistry.registerProvider('tencentcloud', ['custom', 'redblack', 'rollingpush', 'rollingredblack']);
