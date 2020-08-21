import { module } from 'angular';
import { react2angular } from 'react2angular';
import { InstanceLinks } from './InstanceLinks';

export const CORE_INSTANCE_DETAILS_INSTANCELINKS_COMPONENT = 'spinnaker.core.instance.details.instanceLinks';
export const name = CORE_INSTANCE_DETAILS_INSTANCELINKS_COMPONENT; // for backwards compatibility

module(name, []).component(
  'instanceLinks',
  react2angular(InstanceLinks, ['address', 'application', 'instance', 'moniker', 'environment']),
);
