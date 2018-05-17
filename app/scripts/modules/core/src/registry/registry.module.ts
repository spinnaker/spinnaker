import { module } from 'angular';

import { Registry } from './Registry';

export const REGISTRY_MODULE = 'spinnaker.core.registry';

module(REGISTRY_MODULE, []).config(function() {
  Registry.initialize();
});
