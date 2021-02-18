'use strict';

import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/tencentcloud.help';
import { TencentcloudImageReader } from './image';
// load pipeline stage
import './pipeline/stages/disableCluster/disableClusterStage';
import './pipeline/stages/rollbackCluster/rollbackClusterStage';
import './pipeline/stages/scaleDownCluster/scaleDownClusterStage';
import './pipeline/stages/shrinkCluster/shrinkClusterStage';
import { TENCENTCLOUD_REACT_MODULE } from './reactShims/tencentcloud.react.module';
import { TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER } from './search/searchResultFormatter';
import './validation/ApplicationNameValidator';
// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

export const TENCENTCLOUD_MODULE = 'spinnaker.tencentcloud';
module(TENCENTCLOUD_MODULE, [TENCENTCLOUD_REACT_MODULE, TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER]).config(() => {
  CloudProviderRegistry.registerProvider('tencentcloud', {
    name: 'tencentcloud',
    logo: {
      path: require('./logo/tencentcloud.logo.svg'),
    },
    image: {
      reader: TencentcloudImageReader,
    },
  });
});

DeploymentStrategyRegistry.registerProvider('tencentcloud', ['custom', 'redblack', 'rollingpush', 'rollingredblack']);
