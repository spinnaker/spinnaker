'use strict';

import { module } from 'angular';

import { MANAGED_STATES } from './managed.states';

export const CORE_MANAGED_MANAGED_MODULE = 'spinnaker.managed';
module(CORE_MANAGED_MANAGED_MODULE, [MANAGED_STATES]);
