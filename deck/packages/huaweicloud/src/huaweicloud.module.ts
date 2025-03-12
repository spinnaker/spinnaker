'use strict';

import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

export const HUAWEICLOUD_MODULE = 'spinnaker.huaweicloud';
module(HUAWEICLOUD_MODULE, []).config(() => {
  CloudProviderRegistry.registerProvider('huaweicloud', {
    name: 'huaweicloud',
  });
});

DeploymentStrategyRegistry.registerProvider('huaweicloud', ['redblack']);
