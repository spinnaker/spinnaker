import { module } from 'angular';

import { CloudFoundryReactInjector } from './cf.react.injector';

export const CLOUD_FOUNDRY_REACT_MODULE = 'spinnaker.cloudfoundry.react';
module(CLOUD_FOUNDRY_REACT_MODULE, []).run(($injector: any) => {
  // Make angular services importable and (TODO when relevant) convert angular components to react
  CloudFoundryReactInjector.initialize($injector);
});
