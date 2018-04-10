import { IModule, module } from 'angular';

import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';

import { SPINNAKER_HEADER } from 'core/header/spinnakerHeader.component';

export const APPLICATION_BOOTSTRAP_MODULE = 'spinnaker.core.applicationBootstrap';
export const bootstrapModule = module(APPLICATION_BOOTSTRAP_MODULE, [OVERRIDE_REGISTRY, SPINNAKER_HEADER]) as IModule;
