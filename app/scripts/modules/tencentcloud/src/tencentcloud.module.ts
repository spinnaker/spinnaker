'use strict';

import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/tencentcloud.help';
import { TencentcloudImageReader } from './image';
import logo from './logo/tencentcloud.logo.svg';
// load pipeline stage
import './pipeline/stages/disableCluster/disableClusterStage';
import './pipeline/stages/rollbackCluster/rollbackClusterStage';
import './pipeline/stages/scaleDownCluster/scaleDownClusterStage';
import './pipeline/stages/shrinkCluster/shrinkClusterStage';
import { TENCENTCLOUD_REACT_MODULE } from './reactShims/tencentcloud.react.module';
import { TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER } from './search/searchResultFormatter';
import './validation/ApplicationNameValidator';

export const TENCENTCLOUD_MODULE = 'spinnaker.tencentcloud';
module(TENCENTCLOUD_MODULE, [TENCENTCLOUD_REACT_MODULE, TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER]).config(() => {
  CloudProviderRegistry.registerProvider('tencentcloud', {
    name: 'tencentcloud',
    logo: {
      path: logo,
    },
    image: {
      reader: TencentcloudImageReader,
    },
  });
});

DeploymentStrategyRegistry.registerProvider('tencentcloud', ['custom', 'redblack', 'rollingpush', 'rollingredblack']);
