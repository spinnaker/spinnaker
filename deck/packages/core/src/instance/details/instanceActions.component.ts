import { module } from 'angular';

import { InstanceActions } from './InstanceActions';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const CORE_INSTANCE_DETAILS_INSTANCEACTIONS_COMPONENT = 'spinnaker.core.instance.details.instanceActions';
export const name = CORE_INSTANCE_DETAILS_INSTANCEACTIONS_COMPONENT; // for backwards compatibility

module(name, []).component(
  'instanceActions',
  angularComponentFromReact(InstanceActions, 'instanceActions', ['actions', 'title']),
);
