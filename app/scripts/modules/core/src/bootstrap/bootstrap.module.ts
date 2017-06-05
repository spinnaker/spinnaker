import { module } from 'angular';

import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';

export const APPLICATION_BOOTSTRAP_MODULE = 'spinnaker.core.applicationBootstrap';
export const bootstrapModule = module(APPLICATION_BOOTSTRAP_MODULE, [ OVERRIDE_REGISTRY ]);
