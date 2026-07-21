import { module } from 'angular';

import { InstanceInsights } from './InstanceInsights';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const CORE_INSTANCE_DETAILS_INSTANCEINSIGHTS_COMPONENT = 'spinnaker.core.instance.details.instanceInsights';
export const name = CORE_INSTANCE_DETAILS_INSTANCEINSIGHTS_COMPONENT; // for backwards compatibility

module(name, []).component(
  'instanceInsights',
  angularComponentFromReact(InstanceInsights, 'instanceInsights', ['analytics', 'insights', 'instance', 'title']),
);
