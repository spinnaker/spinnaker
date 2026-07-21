import { module } from 'angular';

import { InstanceLinks } from './InstanceLinks';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const CORE_INSTANCE_DETAILS_INSTANCELINKS_COMPONENT = 'spinnaker.core.instance.details.instanceLinks';
export const name = CORE_INSTANCE_DETAILS_INSTANCELINKS_COMPONENT; // for backwards compatibility

module(name, []).component(
  'instanceLinks',
  angularComponentFromReact(InstanceLinks, 'instanceLinks', [
    'address',
    'application',
    'instance',
    'moniker',
    'environment',
  ]),
);
