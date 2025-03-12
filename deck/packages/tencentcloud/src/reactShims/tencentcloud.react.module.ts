import { module } from 'angular';

import { TencentcloudNgReact } from './tencentcloud.ngReact';
import { TencentcloudReactInjector } from './tencentcloud.react.injector';

export const TENCENTCLOUD_REACT_MODULE = 'spinnaker.tencentcloud.react';
module(TENCENTCLOUD_REACT_MODULE, []).run([
  '$injector',
  function ($injector: any) {
    // Make angular services importable and (TODO when relevant) convert angular components to react
    TencentcloudReactInjector.initialize($injector);
    TencentcloudNgReact.initialize($injector);
  },
]);
