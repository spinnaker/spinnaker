import { module } from 'angular';

import { DEBUG_INTERCEPTOR } from './debug.interceptor';

export const INTERCEPTOR_MODULE = 'spinnaker.core.interceptor.module';
module(INTERCEPTOR_MODULE, [DEBUG_INTERCEPTOR]);
