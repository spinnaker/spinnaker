'use strict';

import { module } from 'angular';

import { MANAGED_STATES } from './managed.states';
import { registerNativeResourceKinds } from './resources/registerNativeResourceKinds';

export const CORE_MANAGED_MANAGED_MODULE = 'spinnaker.managed';
module(CORE_MANAGED_MANAGED_MODULE, [MANAGED_STATES]).config(() => {
  registerNativeResourceKinds();
});
