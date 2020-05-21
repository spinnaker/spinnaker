'use strict';

import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';
import './help/tencentcloud.help';
import { TencentCloudImageReader } from './image';
import { TENCENT_SEARCH_SEARCHRESULTFORMATTER } from './search/searchResultFormatter';
import { TENCENT_REACT_MODULE } from './reactShims/tencentcloud.react.module';
import './validation/ApplicationNameValidator';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const TENCENTCLOUD_MODULE = 'spinnaker.tencentcloud';
module(TENCENTCLOUD_MODULE, [TENCENT_REACT_MODULE, TENCENT_SEARCH_SEARCHRESULTFORMATTER]).config(() => {
  CloudProviderRegistry.registerProvider('tencentcloud', {
    name: 'tencentcloud',
    logo: {
      path: require('./logo/tencentcloud.logo.svg'),
    },
    image: {
      reader: TencentCloudImageReader,
    },
  });
});

DeploymentStrategyRegistry.registerProvider('tencentcloud', ['custom', 'redblack', 'rollingpush', 'rollingredblack']);
