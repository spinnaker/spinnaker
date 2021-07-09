import { IModule, module } from 'angular';

import { OVERRIDE_REGISTRY } from '../overrideRegistry/override.registry';
import { SPINNAKER_CONTAINER_COMPONENT } from './spinnakerContainer.component';

export const APPLICATION_BOOTSTRAP_MODULE = 'spinnaker.core.applicationBootstrap';
export const bootstrapModule = module(APPLICATION_BOOTSTRAP_MODULE, [
  OVERRIDE_REGISTRY,
  SPINNAKER_CONTAINER_COMPONENT,
]) as IModule;
