import { module } from 'angular';

import { KubernetesReactInjector } from './kubernetes.react.injector';

export const KUBERNETES_REACT_MODULE = 'spinnaker.kubernetes.react';
module(KUBERNETES_REACT_MODULE, []).run([
  '$injector',
  function ($injector: any) {
    // Make angular services importable and (TODO when relevant) convert angular components to react
    KubernetesReactInjector.initialize($injector);
  },
]);
