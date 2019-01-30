import { IModule, module } from 'angular';

import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';

import { SPINNAKER_HEADER } from 'core/header/spinnakerHeader.component';
import { CUSTOM_BANNER } from 'core/header/customBanner/customBanner.component';

export const APPLICATION_BOOTSTRAP_MODULE = 'spinnaker.core.applicationBootstrap';
export const bootstrapModule = module(APPLICATION_BOOTSTRAP_MODULE, [
  OVERRIDE_REGISTRY,
  SPINNAKER_HEADER,
  CUSTOM_BANNER,
]) as IModule;
