import { module } from 'angular';
import { TitusReactInjector } from './titus.react.injector';

export const TITUS_REACT_MODULE = 'spinnaker.titus.react';
module(TITUS_REACT_MODULE, []).run([
  '$injector',
  function ($injector: any) {
    TitusReactInjector.initialize($injector);
  },
]);
