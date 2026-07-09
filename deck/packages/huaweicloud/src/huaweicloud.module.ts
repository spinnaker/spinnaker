import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

CloudProviderRegistry.registerProvider('huaweicloud', {
  name: 'huaweicloud',
});

DeploymentStrategyRegistry.registerProvider('huaweicloud', ['redblack']);
