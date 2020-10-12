import { module } from 'angular';

import { AwsNgReact } from './aws.ngReact';
import { AwsReactInjector } from './aws.react.injector';

export const AWS_REACT_MODULE = 'spinnaker.amazon.react';
module(AWS_REACT_MODULE, []).run([
  '$injector',
  function ($injector: any) {
    // Make angular services importable and (TODO when relevant) convert angular components to react
    AwsReactInjector.initialize($injector);
    AwsNgReact.initialize($injector);
  },
]);
